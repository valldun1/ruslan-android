# Plan — Chat Fix

## План реализации: Починка чата в Ruslan Agent

### Шаг 1. Создание модели данных
- **Файл:** `model/Message.kt`
- **Класс:** `data class Message`
- **Поля:**
  - `id: String` (UUID)
  - `text: String`
  - `isUser: Boolean` (true — пользователь, false — Hermes)
  - `timestamp: Long`
  - `status: MessageStatus` (enum: PENDING, SENT, ERROR)

### Шаг 2. Настройка UI Layouts (Терминальный стиль)
- **Файл:** `res/values/colors.xml`
  - Добавить: `<color name="terminal_bg">#000000</color>`
  - Добавить: `<color name="terminal_text">#00FF00</color>`
- **Файл:** `res/layout/activity_chat.xml`
  - Root layout: `android:background="@color/terminal_bg"`
  - Добавить: `androidx.recyclerview.widget.RecyclerView` (id: `rvMessages`, `layout_height="0dp"`, `layout_weight="1"`)
  - Добавить: `androidx.appcompat.widget.AppCompatEditText` (id: `etMessageInput`, `textColor="@color/terminal_text"`, `backgroundTint="@color/terminal_text"`)
  - Добавить: `android.widget.ImageButton` (id: `btnSend`, `tint="@color/terminal_text"`)
- **Файл:** `res/layout/item_message.xml`
  - Root: `LinearLayout` (orientation vertical)
  - Добавить: `TextView` (id: `tvMessageText`, `textColor="@color/terminal_text"`, `fontFamily="monospace"`, `textSize="14sp"`)
  - Добавить: `TextView` (id: `tvTimestamp`, `textColor="#888888"`, `fontFamily="monospace"`, `textSize="10sp"`)

### Шаг 3. Реализация адаптера для RecyclerView
- **Файл:** `adapter/MessageAdapter.kt`
- **Класс:** `MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>()`
- **Свойства:** `private val messages: MutableList<Message>`
- **Методы:**
  - `onCreateViewHolder(parent, viewType)`: Инфлейт `item_message.xml`
  - `onBindViewHolder(holder, position)`: Биндит `Message` во `ViewHolder`, применяет отступы (gravity start/end в зависимости от `isUser`)
  - `getItemCount()`: Возвращает `messages.size`
  - `addMessage(message: Message)`: Добавляет в список и вызывает `notifyItemInserted`
  - `updateMessages(newMessages: List<Message>)`: Очищает, добавляет, вызывает `notifyDataSetChanged`
- **Внутренний класс:** `MessageViewHolder(view: View)` — содержит `tvMessage` и `tvTimestamp`

### Шаг 4. Сетевой слой (Hermes Gateway)
- **Файл:** `network/HermesApi.kt`
- **Интерфейс:** `HermesApi`
- **Методы:**
  - `@GET("history") suspend fun getHistory(): Response<List<MessageDto>>`
  - `@POST("message") suspend fun sendMessage(@Body request: MessageRequest): Response<MessageDto>`
- **Файл:** `network/RetrofitClient.kt`
- **Объект:** `RetrofitClient`
- **Свойства:** `val api: HermesApi` (настройка Retrofit + OkHttp + ConverterFactory)
- **Файл:** `repository/ChatRepository.kt`
- **Класс:** `ChatRepository(private val api: HermesApi)`
- **Методы:**
  - `suspend fun loadHistory(): List<Message>` (маппинг DTO в data class)
  - `suspend fun postMessage(text: String): Message` (отправка и получение ответа)

### Шаг 5. Интеграция логики в ChatActivity
- **Файл:** `ChatActivity.kt` (использует существующий View Binding)
- **Свойства:**
  - `private lateinit var binding: ActivityChatBinding`
  - `private lateinit var adapter: MessageAdapter`
  - `private val repository = ChatRepository(RetrofitClient.api)`
- **Метод `onCreate(savedInstanceState)`:**
  - Инициализация `MessageAdapter` пустым списком
  - Настройка `binding.rvMessages`: `layoutManager = LinearLayoutManager(this)`, `adapter = adapter`
  - Вызов `loadHistory()`
  - Установка слушателя `binding.btnSend.setOnClickListener { sendMessage() }`
- **Метод `loadHistory()`:**
  - Запуск `lifecycleScope.launch`
  - Вызов `repository.loadHistory()`
  - Обновление UI: `adapter.updateMessages(history)` (в случае ошибки — показ тоста/сообщения в чате)
- **Метод `sendMessage()`:**
  - Чтение текста из `binding.etMessageInput.text`
  - Если пусто — return
  - Создание `Message(text = input, isUser = true, status = PENDING)`
  - Вызов `adapter.addMessage(userMessage)`, очистка поля ввода
  - Запуск `lifecycleScope.launch`:
    - Вызов `repository.postMessage(input)`
    - При успехе: создание `Message` от Hermes, `adapter.addMessage(botMessage)`
    - При ошибке: обновление статуса `userMessage` на ERROR, `adapter.notifyDataSetChanged()`

### Шаг 6. Финальная полировка терминального стиля
- **Файл:** `res/values/themes.xml` (или styles.xml)
- **Стиль:** `TerminalTheme` (опционально, если нужно переопределить тему активити)
  - `windowBackground`: `#000000`
  - `colorPrimary`: `#00FF00`
- **Файл:** `ChatActivity.kt`
- **Метод:** `onCreate()`
  - Убрать стандартную `ActionBar` (если мешает терминальному виду) через `supportRequestWindowFeature(Window.FEATURE_NO_TITLE)` или тема `NoActionBar`.
  - Скрыть клавиатуру после отправки сообщения.