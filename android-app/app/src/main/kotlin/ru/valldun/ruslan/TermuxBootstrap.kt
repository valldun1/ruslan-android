package ru.valldun.ruslan

import android.content.Context
import android.util.Log
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object TermuxBootstrap {
    
    private const val TAG = "TermuxBootstrap"
    private const val PREFIX_ASSET = "usr.tar.zst"
    private const val HASH_ASSET = "usr.tar.zst.sha256"
    private const val VERSION_FILE = "prefix_version.txt"
    
    @Volatile
    private var isExtracting = false
    
    fun ensurePrefix(context: Context): Boolean {
        val prefixDir = File(getPrefixPath(context))
        
        // Check if already extracted and valid
        if (isPrefixValid(context)) {
            Log.d(TAG, "Prefix already extracted and valid")
            return true
        }
        
        if (isExtracting) {
            Log.d(TAG, "Extraction already in progress")
            return false
        }
        
        isExtracting = true
        
        return try {
            extractPrefix(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract prefix", e)
            false
        } finally {
            isExtracting = false
        }
    }
    
    fun getPrefixPath(context: Context): String {
        return context.getDir("usr", Context.MODE_PRIVATE).absolutePath
    }
    
    private fun isPrefixValid(context: Context): Boolean {
        val prefixDir = File(getPrefixPath(context))
        val versionFile = File(prefixDir, VERSION_FILE)
        
        if (!prefixDir.exists() || !versionFile.exists()) {
            return false
        }
        
        // Check if Python exists
        val pythonBinary = File(prefixDir, "bin/python3")
        if (!pythonBinary.exists()) {
            return false
        }
        
        // Verify version matches asset hash
        try {
            val currentVersion = versionFile.readText().trim()
            val assetHash = getAssetHash(context) ?: return false
            return currentVersion == assetHash
        } catch (e: Exception) {
            Log.e(TAG, "Error checking prefix version", e)
            return false
        }
    }
    
    private fun extractPrefix(context: Context): Boolean {
        Log.d(TAG, "Starting prefix extraction...")
        
        val prefixDir = File(getPrefixPath(context))
        val tempDir = File(context.cacheDir, "prefix_extract")
        
        try {
            // Clean up temp dir
            tempDir.deleteRecursively()
            tempDir.mkdirs()
            
            // Copy asset to temp
            val assetFile = File(tempDir, PREFIX_ASSET)
            context.assets.open(PREFIX_ASSET).use { input ->
                FileOutputStream(assetFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Verify hash
            val expectedHash = getAssetHash(context)
            val actualHash = calculateFileHash(assetFile)
            
            if (expectedHash != null && actualHash != expectedHash) {
                Log.e(TAG, "Hash mismatch! Expected: $expectedHash, got: $actualHash")
                return false
            }
            
            // Clean old prefix
            prefixDir.deleteRecursively()
            prefixDir.mkdirs()
            
            // Extract zstd tarball
            Log.d(TAG, "Extracting ${assetFile.length()} bytes to $prefixDir")
            extractZstdTar(assetFile, prefixDir)
            
            // Fix permissions
            fixPermissions(prefixDir)
            
            // Write version file
            File(prefixDir, VERSION_FILE).writeText(actualHash ?: "unknown")
            
            // Copy config templates
            copyConfigTemplates(context, prefixDir)
            
            Log.d(TAG, "Prefix extraction completed successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            // Clean up on failure
            prefixDir.deleteRecursively()
            return false
        } finally {
            tempDir.deleteRecursively()
        }
    }
    
    private fun extractZstdTar(archive: File, destination: File) {
        // Use zstd binary from assets or system
        val process = ProcessBuilder(
            "zstd", "-d", "-c", archive.absolutePath
        ).start()
        
        // Pipe to tar
        val tarProcess = ProcessBuilder(
            "tar", "-xf", "-", "-C", destination.absolutePath
        ).apply {
            redirectInput(ProcessBuilder.Redirect.PIPE)
        }.start()
        
        // Connect streams
        process.inputStream.use { input ->
            tarProcess.outputStream.use { output ->
                input.copyTo(output)
            }
        }
        
        val zstdExit = process.waitFor()
        val tarExit = tarProcess.waitFor()
        
        if (zstdExit != 0 || tarExit != 0) {
            throw IOException("Extraction failed: zstd=$zstdExit, tar=$tarExit")
        }
    }
    
    private fun fixPermissions(prefixDir: File) {
        // Make binaries executable
        val binDir = File(prefixDir, "bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, false)
                }
            }
        }
        
        // Fix library permissions
        val libDir = File(prefixDir, "lib")
        if (libDir.exists()) {
            libDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".so")) {
                    file.setReadable(true, false)
                }
            }
        }
    }
    
    private fun copyConfigTemplates(context: Context, prefixDir: File) {
        val homeDir = File(prefixDir, "home")
        homeDir.mkdirs()
        
        // Copy config.yaml.example
        try {
            context.assets.open("config/config.yaml.example").use { input ->
                File(homeDir, ".config/hermes/config.yaml").apply {
                    parentFile?.mkdirs()
                    outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy config template", e)
        }
        
        // Copy .env.example
        try {
            context.assets.open("config/.env.example").use { input ->
                File(homeDir, ".env.example").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy env template", e)
        }

        // Copy proxy script
        try {
            val scriptsDir = File(prefixDir, "scripts")
            scriptsDir.mkdirs()
            context.assets.open("scripts/ruslan-proxy.py").use { input ->
                File(scriptsDir, "ruslan-proxy.py").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Proxy script copied")
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy proxy script", e)
        }
    }
    
    private fun getAssetHash(context: Context): String? {
        return try {
            context.assets.open(HASH_ASSET).bufferedReader().use { reader ->
                reader.readLine()?.split(" ")?.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read asset hash", e)
            null
        }
    }
    
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}