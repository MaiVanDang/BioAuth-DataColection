package com.datn.datacollectv2

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.SystemClock
import com.datn.datacollectv2.data.KeystrokeEvent
import com.datn.datacollectv2.data.ScrollEvent
import com.datn.datacollectv2.data.TapEvent
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import androidx.core.content.edit

class FormActivity : AppCompatActivity() {

    private val tapEvents       = mutableListOf<TapEvent>()
    private val keystrokeEvents = mutableListOf<KeystrokeEvent>()
    private val scrollEvents    = mutableListOf<ScrollEvent>()
    private val utcOffsetMs: Long by lazy {
        System.currentTimeMillis() - SystemClock.uptimeMillis()
    }

    private var currentRound = 1
    private var isSubmitted  = false

    // ── Question data ──────────────────────────────────────────────────
    data class McQuestion(val text: String, val choices: List<String>)
    data class TextQuestion(val text: String, val minChars: Int = 0)
    data class ScrollQuestion(val text: String, val items: List<String>)

    private val mcQuestions = listOf(
        McQuestion("Bạn thường đi ngủ vào lúc mấy giờ?",
            listOf("Trước 22h", "22h – 23h", "23h – 0h", "Sau 0h")),
        McQuestion("Bạn thường thức dậy lúc mấy giờ?",
            listOf("Trước 6h", "6h – 7h", "7h – 8h", "Sau 8h")),
        McQuestion("Bạn thường ăn sáng vào thời điểm nào?",
            listOf("Trước 7h", "7h – 8h", "8h – 9h", "Tôi thường không ăn sáng")),
        McQuestion("Bạn thường dành bao nhiêu thời gian cho việc giải trí mỗi ngày?",
            listOf("Dưới 1 giờ", "1 – 2 giờ", "2 – 4 giờ", "Trên 4 giờ")),
        McQuestion("Bạn thường di chuyển đến trường hoặc nơi làm việc bằng gì?",
            listOf("Đi bộ", "Xe đạp", "Xe máy / ô tô", "Phương tiện công cộng")),
        McQuestion("Bạn dùng điện thoại bao nhiêu giờ mỗi ngày?",
            listOf("< 2h", "2h – 4h", "4h – 6h", "> 6h")),
        McQuestion("Bạn thường cầm điện thoại bằng tay nào?",
            listOf("Tay trái", "Tay phải", "Cả hai", "Tuỳ lúc")),
        McQuestion("Bạn đang ở đâu khi làm khảo sát này?",
            listOf("Ở nhà", "Cơ quan", "Ngoài đường", "Nơi khác")),
        McQuestion("Bạn đang ngồi hay đứng?",
            listOf("Đang ngồi", "Đang đứng", "Đang đi", "Đang nằm")),
        McQuestion("Bạn thường sử dụng điện thoại vào thời điểm nào nhiều nhất?",
            listOf("Buổi sáng", "Buổi trưa", "Buổi tối", "Khuya")),
        McQuestion("Bạn thường sử dụng điện thoại ở tư thế nào?",
            listOf("Ngồi", "Đứng", "Nằm", "Vừa đi vừa dùng")),
        McQuestion("Bạn thường dùng điện thoại cho mục đích gì nhiều nhất?",
            listOf("Mạng xã hội", "Học tập / công việc", "Giải trí (video, game)", "Nhắn tin / gọi điện")),
        McQuestion("Bạn thường mở khóa điện thoại bằng cách nào?",
            listOf("Vân tay", "Face ID", "Mã PIN", "Pattern")),
        McQuestion("Bạn thường sử dụng điện thoại trong môi trường nào?",
            listOf("Trong nhà", "Ngoài trời", "Trên phương tiện di chuyển", "Nhiều môi trường khác nhau"))
    )

    private val textQuestions = listOf(
        TextQuestion("Hôm nay bạn đã làm gì? Mô tả ngắn gọn:"),
        TextQuestion("Bạn cảm thấy thế nào hôm nay? Mô tả ngắn:"),
        TextQuestion("Nhập tên đầy đủ của bạn:"),
        TextQuestion("Bạn thường làm gì vào cuối tuần? Mô tả ngắn:"),
        TextQuestion("Hãy kể tên một món ăn bạn thích:"),
        TextQuestion("Bạn thích sử dụng ứng dụng nào nhất trên điện thoại? Vì sao?"),
        TextQuestion("Nhập thành phố hoặc nơi bạn đang sinh sống:")
    )

    private val scrollQuestions = listOf(
        ScrollQuestion("Chọn các hoạt động bạn đã làm hôm nay:",
            listOf("Đọc sách", "Xem phim", "Tập thể dục", "Nấu ăn",
                "Học bài", "Chơi game", "Mua sắm", "Làm việc",
                "Nghe nhạc", "Đi dạo", "Gặp bạn bè", "Ngủ trưa")),
        ScrollQuestion("Chọn ứng dụng bạn dùng nhiều nhất:",
            listOf("Facebook", "YouTube", "TikTok", "Zalo",
                "Instagram", "Gmail", "Chrome", "Spotify",
                "Google Maps", "Netflix", "Messenger", "Shopee")),
        ScrollQuestion("Bạn cảm thấy việc sử dụng ứng dụng thu thập dữ liệu này như thế nào?",
            listOf("Rất dễ sử dụng", "Khá dễ sử dụng", "Bình thường",
                "Hơi khó sử dụng", "Giao diện chưa rõ ràng", "Thao tác hơi nhiều",
                "Ứng dụng chạy mượt", "Ứng dụng hơi chậm", "Tôi thấy thú vị",
                "Tôi thấy bình thường", "Tôi muốn dùng lại", "Tôi không muốn dùng lại")),

        ScrollQuestion("Bạn nghĩ gì về việc sử dụng dữ liệu hành vi (touch, sensor) để xác thực người dùng?",
            listOf("Rất hữu ích cho bảo mật", "Khá hữu ích", "Bình thường", "Tôi chưa hiểu rõ",
                "Có thể giúp tăng an toàn", "Có thể thay thế mật khẩu", "Tôi thấy ý tưởng thú vị", "Tôi thấy khá mới lạ",
                "Có thể ứng dụng trong tương lai", "Có thể dùng cho ngân hàng", "Có thể dùng cho điện thoại", "Tôi cần tìm hiểu thêm")),

        ScrollQuestion("Trong quá trình dùng ứng dụng này, bạn gặp trải nghiệm nào sau đây?",
            listOf("Ứng dụng dễ hiểu", "Hướng dẫn rõ ràng", "Thu dữ liệu nhanh", "Phải thao tác nhiều",
                "Cần thêm hướng dẫn", "Giao diện đẹp", "Giao diện đơn giản", "Ứng dụng ổn định", "Có lúc bị lag nhẹ",
                "Tôi thấy quá trình thu dữ liệu thú vị", "Tôi hiểu hơn về đề tài nghiên cứu", "Tôi muốn thử thêm các chức năng khác"))
    )

    private val mcSelectedIndex = mutableListOf<Int>().apply {
        repeat(mcQuestions.size) { add(-1) }
    }
    private val mcChoiceCards  = mutableListOf<List<MaterialCardView>>()
    private val mcChoiceTexts  = mutableListOf<List<TextView>>()
    private val textEditTexts  = mutableListOf<EditText>()
    private val scrollCheckboxes = mutableListOf<List<CheckBox>>()

    private lateinit var btnLogout         : ImageButton
    private lateinit var tvUserInfo        : TextView
    private lateinit var tvUserAvatar      : TextView

    // ── Views ──────────────────────────────────────────────────────────
    private lateinit var btnBack      : ImageButton
    private lateinit var btnNext      : MaterialButton
    private lateinit var formContainer: LinearLayout
    private lateinit var mainScrollView: androidx.core.widget.NestedScrollView

    // ── Lifecycle ──────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form)

        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        btnLogout         = findViewById(R.id.btnLogout)
        tvUserInfo        = findViewById(R.id.tvUserInfo)
        tvUserAvatar      = findViewById(R.id.tvUserAvatar)

        formContainer  = findViewById(R.id.formContainer)
        mainScrollView = findViewById(R.id.mainScrollView)

        btnNext = findViewById(R.id.btnNext)
        currentRound = intent.getIntExtra("ROUND", 1)
        updateNextButton()
        btnNext.setOnClickListener { onSubmitClicked() }

        UserSession.getProfile(this)?.let { profile ->
            tvUserInfo.text = profile.name

            val initials = profile.name
                .trim().split("\\s+".toRegex())
                .filter { it.isNotEmpty() }
                .takeLast(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .take(2)
                .ifEmpty { "U" }
            tvUserAvatar.text = initials
        }

        btnLogout.setOnClickListener { showLogoutDialog() }

        setupScrollTracking()
        buildAllQuestions()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── Logout ─────────────────────────────────────────────────────────

    private fun showLogoutDialog() {

        AlertDialog.Builder(this)
            .setTitle("Đăng xuất?")
            .setMessage(
                "Dữ liệu cảm biến đã thu và câu trả lời form đã nhập sẽ bị xóa.\n\n" +
                        "Bạn có thể đăng nhập lại bằng tài khoản mới."
            )
            .setPositiveButton("Đăng xuất") { _, _ ->
                stopService(Intent(this, SensorForegroundService::class.java))
                UserSession.logout(this)
                Intent(this, RegistrationActivity::class.java).also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(it)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ── Build toàn bộ câu hỏi một lần ────────────────────────────────
    private fun buildAllQuestions() {
        formContainer.removeAllViews()
        mcChoiceCards.clear()
        mcChoiceTexts.clear()
        textEditTexts.clear()
        scrollCheckboxes.clear()

        addSectionHeader("Phần 1: Câu hỏi lựa chọn")
        mcQuestions.forEachIndexed { idx, q ->
            addMcQuestionCard(idx, q)
        }

        addSectionHeader("Phần 2: Câu hỏi nhập văn bản")
        textQuestions.forEachIndexed { idx, q ->
            addTextQuestionCard(idx, q)
        }

        addSectionHeader("Phần 3: Câu hỏi chọn nhiều")
        scrollQuestions.forEachIndexed { idx, q ->
            addScrollQuestionCard(idx, q)
        }
        restoreDraft()
    }

    // ── Section header ────────────────────────────────────────────────
    private fun addSectionHeader(title: String) {
        val tv = TextView(this).apply {
            text      = title
            textSize  = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.teal_600))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dpToPx(4), dpToPx(16), dpToPx(4), dpToPx(4))
            layoutParams = lp
        }
        formContainer.addView(tv)
    }

    // ── MC question card ──────────────────────────────────────────────
    private fun addMcQuestionCard(qIdx: Int, q: McQuestion) {
        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(6), 0, dpToPx(6))
            layoutParams    = lp
            radius          = dpToPx(12).toFloat()
            cardElevation   = 0f
            strokeWidth     = 0
            setCardBackgroundColor(getColor(R.color.surface))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
        }

        inner.addView(TextView(this).apply {
            text      = "Câu ${qIdx + 1}/${mcQuestions.size}"
            textSize  = 14f
            setTextColor(getColor(R.color.secondary_text))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(6))
            layoutParams = lp
        })

        inner.addView(TextView(this).apply {
            text      = q.text
            textSize  = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.on_surface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(14))
            layoutParams = lp
        })

        val cardList = mutableListOf<MaterialCardView>()
        val textList = mutableListOf<TextView>()

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(8))
            layoutParams = lp
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        q.choices.forEachIndexed { ci, choiceText ->
            val choiceCard = MaterialCardView(this).apply {
                val lp = LinearLayout.LayoutParams(0, dpToPx(48), 1f)
                if (ci % 2 == 0) lp.marginEnd = dpToPx(6)
                layoutParams  = lp
                radius        = dpToPx(8).toFloat()
                cardElevation = 0f
                strokeWidth   = dpToPx(1)
                strokeColor   = getColor(R.color.divider)
                setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                isClickable    = true
                isFocusable    = true
            }
            val choiceTv = TextView(this).apply {
                text      = choiceText
                textSize  = 14f
                gravity   = android.view.Gravity.CENTER
                setTextColor(getColor(R.color.secondary_text))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            choiceCard.addView(choiceTv)
            cardList.add(choiceCard)
            textList.add(choiceTv)

            if (ci < 2) row1.addView(choiceCard) else row2.addView(choiceCard)

            val answerId = "mc_q${qIdx + 1}_choice_${('A' + ci)}"
            var downEventTime = 0L
            choiceCard.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downEventTime = event.eventTime   // lưu raw, chưa cộng offset
                        tapEvents.add(
                            TapEvent(utcOffsetMs + event.eventTime, event.x, event.y,
                                event.pressure, event.size, answerId, "DOWN")
                        )
                    }
                    MotionEvent.ACTION_UP -> {
                        val holdMs = event.eventTime - downEventTime   // đúng: raw - raw
                        tapEvents.add(TapEvent(utcOffsetMs + event.eventTime, event.x, event.y,
                            event.pressure, event.size, answerId, "UP", holdMs))
                        selectMcChoice(qIdx, ci, cardList, textList)
                    }
                }
                true
            }
        }

        inner.addView(row1)
        inner.addView(row2)
        card.addView(inner)
        formContainer.addView(card)

        mcChoiceCards.add(cardList)
        mcChoiceTexts.add(textList)
    }

    private fun selectMcChoice(qIdx: Int, choiceIdx: Int,
                               cards: List<MaterialCardView>,
                               texts: List<TextView>) {
        mcSelectedIndex[qIdx] = choiceIdx
        cards.forEachIndexed { i, c ->
            if (i == choiceIdx) {
                c.strokeColor = getColor(R.color.blue_400)
                c.setCardBackgroundColor(getColor(R.color.blue_50))
                texts[i].setTextColor(getColor(R.color.blue_800))
                texts[i].setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                c.strokeColor = getColor(R.color.divider)
                c.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                texts[i].setTextColor(getColor(R.color.secondary_text))
                texts[i].setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    // ── Text question card ────────────────────────────────────────────
    private fun addTextQuestionCard(qIdx: Int, q: TextQuestion) {
        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(6), 0, dpToPx(6))
            layoutParams  = lp
            radius        = dpToPx(12).toFloat()
            cardElevation = 0f
            strokeWidth   = 0
            setCardBackgroundColor(getColor(R.color.surface))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
        }

        inner.addView(TextView(this).apply {
            text      = "Câu ${qIdx + 1}/${textQuestions.size}"
            textSize  = 14f
            setTextColor(getColor(R.color.secondary_text))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(6))
            layoutParams = lp
        })

        inner.addView(TextView(this).apply {
            text      = q.text
            textSize  = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.on_surface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(10))
            layoutParams = lp
        })

        val inputCard = MaterialCardView(this).apply {
            layoutParams  = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            radius        = dpToPx(8).toFloat()
            cardElevation = 0f
            strokeWidth   = dpToPx(1)
            strokeColor   = getColor(R.color.teal_400)
            setCardBackgroundColor(getColor(R.color.surface_secondary))
        }

        val et = EditText(this).apply {
            layoutParams  = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minHeight     = dpToPx(80)
            hint          = "Nhập câu trả lời..."
            textSize      = 15f
            setTextColor(getColor(R.color.on_surface))
            background    = null
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            gravity       = android.view.Gravity.TOP
            inputType     = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        inputCard.addView(et)
        inner.addView(inputCard)

        val tvCharCount = TextView(this).apply {
            text      = "0 ký tự"
            textSize  = 12f
            setTextColor(getColor(R.color.hint_text))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = android.view.Gravity.END
            lp.setMargins(0, dpToPx(4), 0, 0)
            layoutParams = lp
        }
        inner.addView(tvCharCount)

        var lastKeyUptime = 0L
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val nowUptime = SystemClock.uptimeMillis()
                val nowMs     = utcOffsetMs + nowUptime
                keystrokeEvents.add(KeystrokeEvent(
                    timestamp_ms = nowMs,
                    field_id     = "text_q${qIdx + 1}",
                    char_count   = s?.length ?: 0,
                    inter_key_ms = if (lastKeyUptime > 0) nowUptime - lastKeyUptime else 0,
                    is_delete    = count == 0
                ))
                lastKeyUptime    = nowUptime
                tvCharCount.text = "${s?.length ?: 0} ký tự"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        card.addView(inner)
        formContainer.addView(card)
        textEditTexts.add(et)
    }

    // ── Scroll/Checkbox question card ─────────────────────────────────
    private fun addScrollQuestionCard(qIdx: Int, q: ScrollQuestion) {
        val card = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dpToPx(6), 0, dpToPx(6))
            layoutParams  = lp
            radius        = dpToPx(12).toFloat()
            cardElevation = 0f
            strokeWidth   = 0
            setCardBackgroundColor(getColor(R.color.surface))
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
        }

        inner.addView(TextView(this).apply {
            text      = "Danh sách ${qIdx + 1}/${scrollQuestions.size}"
            textSize  = 14f
            setTextColor(getColor(R.color.secondary_text))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(6))
            layoutParams = lp
        })

        inner.addView(TextView(this).apply {
            text      = q.text
            textSize  = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.on_surface))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, dpToPx(10))
            layoutParams = lp
        })

        val checkboxList = mutableListOf<CheckBox>()
        q.items.forEach { item ->
            val cb = CheckBox(this).apply {
                text      = item
                textSize  = 14f
                setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
                setTextColor(getColor(R.color.on_surface))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            inner.addView(cb)
            checkboxList.add(cb)
            inner.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(getColor(R.color.divider))
            })
        }

        card.addView(inner)
        formContainer.addView(card)
        scrollCheckboxes.add(checkboxList)
    }

    // ── Submit ────────────────────────────────────────────────────────
    private fun onSubmitClicked() {
        val unansweredMc = mcSelectedIndex.indexOfFirst { it < 0 }
        if (unansweredMc >= 0) {
            android.widget.Toast.makeText(
                this,
                "Vui lòng trả lời câu hỏi trắc nghiệm số ${unansweredMc + 1}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val unansweredText = textEditTexts.indexOfFirst { it.text.isBlank() }
        if (unansweredText >= 0) {
            android.widget.Toast.makeText(
                this,
                "Vui lòng điền câu hỏi nhập liệu số ${unansweredText + 1}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        isSubmitted = true
        saveFormData()
        clearDraft()

        if (currentRound < 2) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Hoàn thành lần $currentRound!")
                .setMessage(
                    "Bạn đã điền xong lần $currentRound.\n\n" +
                            "Theo kịch bản thu, vui lòng điện lại form thêm 1 lần nữa " +
                            "(~5 phút) để tăng gấp đôi số mẫu.\n\n" +
                            "Nhấn \"Điền lại\" để bắt đầu lần ${currentRound + 1}, " +
                            "hoặc \"Hoàn tất\" nếu bạn muốn kết thúc."
                )
                .setPositiveButton("Điền lại") { _, _ ->
                    currentRound++
                    isSubmitted = false
                    updateNextButton()
                    resetFormData()
                    buildAllQuestions()
                    mainScrollView.scrollTo(0, 0)
                }
                .setNegativeButton("Hoàn tất") { _, _ ->
                    navigateToUpload()
                }
                .setCancelable(false)
                .show()
        } else {
            navigateToUpload()
        }
    }

    private fun updateNextButton() {
        btnNext.text = if (currentRound < 2) "Hoàn thành lần $currentRound →" else "Hoàn tất ✓"
    }

    private fun resetFormData() {
        tapEvents.clear()
        keystrokeEvents.clear()
        scrollEvents.clear()
        mcSelectedIndex.replaceAll { -1 }
    }

    private fun navigateToUpload() {
        val uploadIntent = Intent(this, UploadActivity::class.java).apply {
            putExtra("USER_ID", this@FormActivity.intent.getStringExtra("USER_ID"))
        }
        startActivity(uploadIntent)
        finish()
    }

    private fun clearDraft() {
        getPref().edit().clear().apply()
    }

    // ── Scroll tracking ───────────────────────────────────────────────
    private fun setupScrollTracking() {
        mainScrollView = findViewById(R.id.mainScrollView)
        mainScrollView.setOnTouchListener { _, event ->

            val action = event.actionMasked
            val pointerIndex = event.actionIndex
            val pointerId = event.getPointerId(pointerIndex)

            when (action) {

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrollEvents.add(
                        ScrollEvent(
                            timestamp_ms = utcOffsetMs + event.eventTime,
                            x = event.getX(pointerIndex),
                            y = event.getY(pointerIndex),
                            pressure = event.getPressure(pointerIndex),
                            size = event.getSize(pointerIndex),
                            phase = "DOWN",
                            pointer_id = pointerId
                        )
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        scrollEvents.add(
                            ScrollEvent(
                                timestamp_ms = utcOffsetMs + event.eventTime,
                                x = event.getX(i),
                                y = event.getY(i),
                                pressure = event.getPressure(i),
                                size = event.getSize(i),
                                phase = "MOVE",
                                pointer_id = event.getPointerId(i)
                            )
                        )
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    scrollEvents.add(
                        ScrollEvent(
                            timestamp_ms = utcOffsetMs + event.eventTime,
                            x = event.getX(pointerIndex),
                            y = event.getY(pointerIndex),
                            pressure = event.getPressure(pointerIndex),
                            size = event.getSize(pointerIndex),
                            phase = "UP",
                            pointer_id = pointerId
                        )
                    )
                }
            }

            false
        }
    }

    // ── Save ──────────────────────────────────────────────────────────
    private fun saveFormData() {
        val userId    = intent.getStringExtra("USER_ID") ?: "unknown"
        val sessionId = getSharedPreferences("session_prefs", MODE_PRIVATE)
            .getString("current_session_id", "session_1") ?: "session_1"

        val dir = File(getExternalFilesDir(null), "$userId/$sessionId")
        dir.mkdirs()

        saveListToCSV(tapEvents,       dir, "tap_r${currentRound}.csv",        sessionId)
        saveListToCSV(keystrokeEvents, dir, "keystroke_r${currentRound}.csv",  sessionId)
        saveListToCSV(scrollEvents,    dir, "scroll_r${currentRound}.csv",     sessionId)
    }

    private fun <T> saveListToCSV(list: List<T>, dir: File, fileName: String, sessionId: String = "") {
        if (list.isEmpty()) return
        dir.mkdirs()
        File(dir, fileName).bufferedWriter().use { writer ->
            when {
                list.first() is TapEvent -> {
                    writer.write("timestamp_ms,x,y,pressure,size,phase,hold_ms,session_id")
                    writer.newLine()
                    @Suppress("UNCHECKED_CAST")
                    (list as List<TapEvent>).forEach { e ->
                        writer.write("${e.timestamp_ms},${e.x},${e.y},${e.pressure},${e.size},${e.phase},${e.hold_ms},$sessionId")
                        writer.newLine()
                    }
                }
                list.first() is KeystrokeEvent -> {
                    writer.write("timestamp_ms,field_id,char_count,inter_key_ms,is_delete,session_id")
                    writer.newLine()
                    @Suppress("UNCHECKED_CAST")
                    (list as List<KeystrokeEvent>).forEach { e ->
                        writer.write("${e.timestamp_ms},${e.field_id},${e.char_count},${e.inter_key_ms},${e.is_delete},$sessionId")
                        writer.newLine()
                    }
                }
                list.first() is ScrollEvent -> {
                    writer.write("timestamp_ms,x,y,pressure,size,phase,pointer_id,session_id")
                    writer.newLine()
                    @Suppress("UNCHECKED_CAST")
                    (list as List<ScrollEvent>).forEach { e ->
                        writer.write("${e.timestamp_ms},${e.x},${e.y},${e.pressure},${e.size},${e.phase},${e.pointer_id},$sessionId")
                        writer.newLine()
                    }
                }
                else -> {
                    val fields = list.first()!!::class.java.declaredFields
                    writer.write(fields.joinToString(",") { it.name } + ",session_id")
                    writer.newLine()
                    list.forEach { item ->
                        writer.write(fields.joinToString(",") { field ->
                            field.isAccessible = true
                            field.get(item)?.toString() ?: ""
                        } + ",$sessionId")
                        writer.newLine()
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    // ── Constants ──────────────────────────────────────────────────────
    companion object {
        private const val PREF_NAME = "form_draft"
    }

    private fun getPref() = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

    override fun onStop() {
        super.onStop()
        if (!isSubmitted) saveDraft()
    }

    private fun saveDraft() {
        val currentSession = getSharedPreferences("session_prefs", MODE_PRIVATE)
            .getString("current_session_id", "session_1") ?: "session_1"
        getPref().edit {
            putString("session_id", currentSession)
            putInt("round", currentRound)

            mcSelectedIndex.forEachIndexed { idx, selected ->
                putInt("mc_$idx", selected)
            }

            textEditTexts.forEachIndexed { idx, et ->
                putString("text_$idx", et.text.toString())
            }

            scrollCheckboxes.forEachIndexed { qIdx, checkboxes ->
                checkboxes.forEachIndexed { cIdx, cb ->
                    putBoolean("scroll_${qIdx}_$cIdx", cb.isChecked)
                }
            }

        }
    }

    private fun restoreDraft() {
        val pref = getPref()
        val currentSession = getSharedPreferences("session_prefs", MODE_PRIVATE)
            .getString("current_session_id", "session_1") ?: "session_1"
        val savedSession   = pref.getString("session_id", "")
        if (savedSession != currentSession) {
            clearDraft()
            return
        }

        val savedRound = pref.getInt("round", currentRound)
        if (savedRound != currentRound) {
            currentRound = savedRound
            updateNextButton()
        }

        mcSelectedIndex.forEachIndexed { idx, _ ->
            val saved = pref.getInt("mc_$idx", -1)
            if (saved >= 0) {
                mcSelectedIndex[idx] = saved
                selectMcChoice(idx, saved, mcChoiceCards[idx], mcChoiceTexts[idx])
            }
        }

        textEditTexts.forEachIndexed { idx, et ->
            val saved = pref.getString("text_$idx", "")
            if (!saved.isNullOrEmpty()) et.setText(saved)
        }

        scrollCheckboxes.forEachIndexed { qIdx, checkboxes ->
            checkboxes.forEachIndexed { cIdx, cb ->
                cb.isChecked = pref.getBoolean("scroll_${qIdx}_$cIdx", false)
            }
        }
    }

}