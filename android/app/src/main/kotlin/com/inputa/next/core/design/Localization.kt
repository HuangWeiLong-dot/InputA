package com.inputa.next.core.design

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** 界面语言。默认英文，可在 Settings → Interface Language 切换。 */
enum class AppLanguage(val code: String, val label: String) {
    English("en", "English"),
    Chinese("zh", "简体中文"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: English
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.English }

private val ZH: Map<String, String> = mapOf(
    // 通用
    "Home" to "首页",
    "Explore" to "发现",
    "Playlist" to "播放列表",
    "Vocabulary" to "词库",
    "Settings" to "设置",
    "Back" to "返回",
    "Close" to "关闭",
    "Search" to "搜索",
    "More" to "更多",
    "Create" to "新建",
    "All Stats" to "全部统计",
    "Try again" to "重试",
    "Retry" to "重试",
    "Done" to "完成",
    "Cancel" to "取消",
    "Save" to "保存",
    "Skip" to "跳过",
    "View All" to "查看全部",
    "See all" to "查看全部",
    "Refresh" to "刷新",

    // 登录
    "Sign in to your LingQ account" to "登录 LingQ 账号",
    "LingQ Token" to "LingQ Token",
    "Username or email" to "用户名 / 邮箱",
    "Password" to "密码",
    "Signing in…" to "正在登录…",
    "Sign in with token" to "用 Token 登录",
    "Sign in" to "登录",
    "For personal testing only. Connects directly to LingQ; your password is never stored." to
        "仅用于自用测试，直连 LingQ 官方接口；密码不会被保存。",
    "Open LingQ in a desktop browser → F12 → Network → any www.lingq.com/api request → " +
        "copy everything after \"Authorization: Token\" in the request headers." to
        "在电脑浏览器登录 LingQ → F12 → Network → 任一 www.lingq.com/api 请求 → 复制 Request Headers 里 Authorization: Token 后面的整串。",
    "Enter your LingQ username and password" to "请输入 LingQ 用户名和密码",
    "Paste your LingQ token" to "请粘贴 LingQ Token",

    // 首页
    "Loading your lessons…" to "正在加载课程…",
    "No lessons yet" to "还没有课程",
    "Find something you want to read or listen to in Explore." to "去发现页找一些想读或想听的内容。",
    "Explore lessons" to "去发现课程",
    "Known Words" to "已知词汇",
    "Ignored" to "已忽略",
    "Total" to "总计",
    "Continue Studying" to "继续学习",
    "Picked For You" to "为你推荐",
    "CONTINUE WHERE YOU LEFT OFF" to "继续上次阅读",
    "Saved Lessons" to "已收藏课程",
    "Saved lesson" to "已收藏课程",
    "Beginner" to "初级",
    "Intermediate" to "中级",
    "Advanced" to "高级",
    "Beg." to "初",
    "Int." to "中",
    "Adv." to "高",
    "Course level" to "课程等级",
    "More for you" to "更多推荐",
    "All levels" to "全部等级",
    "No activity data was returned for this period." to "该时间段没有活动数据。",
    "This Week" to "本周",
    "Day Streak" to "连续天数",
    "Words Read" to "已读词数",
    "Hrs Listened" to "收听时长",
    "Daily Goal" to "每日目标",
    "Loading your stats…" to "正在加载统计…",
    "Your local learning progress" to "你的本机学习进度",
    "Word Library" to "词库统计",
    "Total tracked" to "已记录词汇",
    "Goal reached today." to "今日目标已完成。",
    "Stats are calculated from word statuses saved on this device." to "统计根据保存在此设备上的词汇状态计算。",

    // 课程详情
    "Bookmark" to "收藏",
    "Saved" to "已收藏",
    "In Playlist" to "已加入列表",
    "Complete" to "完成",
    "Review" to "复习",
    "Lesson Description" to "课程简介",
    "Study Tools" to "学习工具",
    "Review Inputas" to "复习 Inputa 单词",
    "Flip through the words you are learning" to "翻卡复习正在学习的词",
    "Ask about this lesson or its words" to "询问这节课或其中的单词",
    "Lesson Info" to "课程信息",
    "Level" to "等级",
    "Duration" to "时长",
    "Collection" to "合集",
    "Status" to "状态",
    "Completed" to "已完成",
    "Saved for later" to "已收藏",
    "Not started" to "未开始",
    "Start Lesson" to "开始课程",
    "Read Again" to "再读一遍",
    "Total Words" to "总词数",
    "Unique Words" to "独立词数",
    "New Words" to "生词数",
    "Lesson marked complete" to "已标记课程完成",
    "Saved to bookmarks" to "已加入收藏",
    "Bookmark removed" to "已取消收藏",
    "Added to playlist" to "已加入播放列表",
    "Already in playlist" to "已在播放列表中",
    "This lesson has no audio." to "这节课没有音频。",
    "Continue Course" to "继续课程",
    "Start Course" to "开始课程",
    "Loading course…" to "正在加载课程…",
    "LingQ didn't return the lessons for this course yet." to "LingQ 还没有返回这门课程的单集。",

    // 阅读器
    "Reading settings" to "阅读设置",
    "Reading Settings" to "阅读设置",
    "Review words" to "复习单词",
    "AI Tutor" to "AI 导师",
    "Fullscreen" to "全屏",
    "Exit fullscreen" to "退出全屏",
    "Mark lesson complete" to "标记课程完成",
    "Loading lesson…" to "正在加载课程…",
    "This lesson has no readable text yet. If it was just imported, LingQ may still be generating it — wait a moment and retry." to
        "这节课暂时没有可读正文。如果是刚导入的课程，LingQ 可能还在生成，稍后重试。",
    "Reading mode" to "阅读模式",
    "Page Mode" to "分页模式",
    "Sentence Mode" to "句子模式",
    "Scroll Mode" to "滚动模式",
    "Font Size" to "字号",
    "Line Spacing" to "行距",
    "Paragraph Spacing" to "段间距",
    "Word Spacing" to "词间距",
    "The quick brown fox jumps over the lazy dog." to "敏捷的棕色狐狸跳过了懒狗。",
    "Play from here" to "从这里播放",
    "Looking up…" to "正在查询…",
    "No translation available" to "暂无释义",
    "Ignore" to "忽略",
    "Known" to "已知",

    // 复习
    "Preparing review…" to "正在准备复习…",
    "All caught up!" to "全部完成！",
    "No Inputa words are due right now." to "当前没有需要复习的 Inputa 单词。",
    "No Inputa words available to review." to "没有可复习的 Inputa 单词。",
    "Tap to reveal" to "点击显示答案",
    "Show answer" to "显示答案",
    "Don't know" to "不记得",
    "Know" to "认识",
    "Review again" to "再复习一遍",
    "No saved meaning" to "暂无已保存释义",

    // 词库
    "Search your vocabulary" to "搜索词库",
    "Loading vocabulary…" to "正在加载词库…",
    "No Inputa words yet" to "还没有 Inputa 单词",
    "No matching terms" to "没有匹配的单词",
    "Tap a blue word in the Reader to save it here." to "在阅读器里点蓝色单词即可收录到这里。",
    "Try a different filter or search term." to "换一个筛选条件或关键词试试。",
    "STATUS" to "状态",
    "Delete word" to "删除单词",

    // 发现
    "What interests you?" to "你对什么感兴趣？",
    "All resources" to "全部来源",
    "Loading library…" to "正在加载资料库…",
    "Searching…" to "正在搜索…",
    "We could not find any lessons!" to "没有找到任何课程！",
    "Try another level or keyword." to "换个等级或关键词试试。",
    "No results" to "没有结果",
    "No lessons from this source yet. Try another source or level." to "这个来源还没有课程，换个来源或等级试试。",
    "Try another keyword or choose a different level." to "换个关键词或选择其他等级。",

    // 收藏
    "No saved lessons" to "还没有收藏课程",
    "Open a lesson and tap Bookmark to keep it here." to "打开课程并点击收藏，就会出现在这里。",
    "Remove bookmark" to "取消收藏",

    // 设置与面板
    "LEARNING" to "学习",
    "AI" to "AI",
    "READER" to "阅读器",
    "LANGUAGES" to "语言",
    "APP" to "应用",
    "HELP" to "帮助",
    "ACCOUNT" to "账号",
    "Review Settings" to "复习设置",
    "Word Popup" to "词弹窗",
    "Lesson Settings" to "课程设置",
    "Stats" to "统计",
    "Import Lesson" to "导入课程",
    "Provider" to "服务商",
    "Model" to "模型",
    "Not configured" to "未配置",
    "Edit AI settings" to "编辑 AI 设置",
    "Connect your AI" to "接入你的 AI",
    "Auto Text-to-Speech" to "自动朗读",
    "Active Language" to "当前学习语言",
    "Interface Language" to "界面语言",
    "Theme" to "主题",
    "Notifications" to "通知",
    "Dark" to "深色",
    "Off" to "关闭",
    "AI explanation" to "AI 单词解释",
    "Explanation language" to "解释语言",
    "Generating…" to "正在生成…",
    "Cached locally" to "已缓存到本机",
    "Regenerate" to "重新生成",
    "AI is not configured" to "还没有配置 AI",
    "Open AI settings" to "打开 AI 设置",
    "Meaning" to "释义",
    "Grammar" to "语法",
    "Usage" to "用法",
    "Examples" to "例句",
    "The LingQ Method" to "LingQ 方法",
    "Help & Support" to "帮助与支持",
    "Signed in as" to "当前账号",
    "Sign Out" to "退出登录",
    "How many words you aim to learn each day" to "每天计划学习多少词",
    "words per day" to "词 / 天",
    "A word counts once on the day its status changes. Adjust in steps of 100." to
        "同一个词每天只计一次，仅在状态变化时计数。每次调整 100。",
    "Tune your flashcard sessions" to "调整闪卡复习",
    "Cards per session" to "每次卡片数",
    "All" to "全部",
    "Shuffle cards" to "打乱卡片",
    "Mix levels instead of reviewing lowest levels first." to "混合不同等级，而不是先复习最低等级。",
    "What happens when you tap a word" to "点词时的行为",
    "Pronounce on tap" to "点词自动发音",
    "Play the word audio as soon as the popup opens." to "弹窗出现时立即播放单词发音。",
    "Add new words automatically" to "自动收录新词",
    "Tapping a word with no status saves it as a level 1 Inputa." to "点击未标记的词时自动保存为 1 级 Inputa。",
    "Defaults for newly opened lessons" to "新打开课程的默认设置",
    "Shows one sentence at a time with next/previous controls." to "一次显示一句，并提供上一句/下一句。",
    "Shows the whole lesson in one continuous scroll." to "整节课连续滚动显示。",
    "Splits the lesson into full-screen pages." to "把课程拆成整屏分页。",
    "A daily nudge to keep reading" to "每天提醒你继续阅读",
    "Daily reading reminder" to "每日阅读提醒",
    "Get one local notification a day at the time you pick." to "每天在你选择的时间发送一条本地通知。",
    "Reminder time" to "提醒时间",
    "Notifications are scheduled on this device. Android may slightly delay them while the phone is idle." to
        "提醒在本机调度，手机休眠时 Android 可能略有延迟。",
    "Appearance" to "外观",
    "This build is dark-only. Light and parchment themes are not available yet." to "当前版本仅支持深色主题。",
    "Language of menus and buttons" to "菜单与按钮的语言",
    "Changes apply immediately." to "修改后立即生效。",
    "Notification permission is required for reminders." to "需要通知权限才能开启提醒。",
    "Time to read" to "该阅读了",
    "A few minutes now keeps your streak alive." to "现在读几分钟，保持连续学习。",
    "Daily reading reminder" to "每日阅读提醒",

    // 帮助内容
    "How learning works in this app" to "这个应用如何帮助学习",
    "Answers to the common questions" to "常见问题解答",
    "Input, not memorisation" to "输入优先，而不是死记硬背",
    "You learn by reading and listening to content you mostly understand. Keep the material slightly above your level and let context do the work." to
        "通过阅读和收听大部分能理解的内容来学习。材料略高于当前水平，让语境发挥作用。",
    "Known Words and Inputas" to "已知词与 Inputa",
    "White words are known. Yellow words are Inputas you are still learning, with a level from 1 to 4. Blue words are new: tap one to see meanings and save it as an Inputa." to
        "白色是已知词，黄色是正在学习的 Inputa（1–4 级），蓝色是新词：点一下查看释义并保存为 Inputa。",
    "Move words forward" to "让单词逐步进阶",
    "Reviewing an Inputa raises its level. When it reaches the top, mark it known and keep going. Ignoring a word hides it from your learning counts." to
        "复习会提升 Inputa 等级，到顶后标记为已知即可。忽略的词不会计入学习统计。",
    "Small steps every day" to "每天一点，持续积累",
    "A steady daily goal beats occasional long sessions. Set a comfortable word target in Settings and let the streak build itself." to
        "稳定的每日目标比偶尔长时间学习更有效。在设置里定一个舒服的词数目标，让连续天数自然增长。",
    "Reading" to "阅读",
    "Tap any word for meanings and controls. Font size, line spacing and the default reading mode live under Settings." to
        "点击任意单词查看释义和操作。字号、行距和默认阅读模式都在设置里。",
    "Words and review" to "单词与复习",
    "Word status is stored on this device. Review sessions draw from your Inputas; you can limit their size or shuffle them in Review Settings." to
        "单词状态保存在本机。复习会从 Inputa 中抽卡，可在复习设置里限制数量或打乱顺序。",
    "Audio and AI" to "音频与 AI",
    "Word pronunciation uses LingQ audio with a system voice fallback. The AI Tutor uses the provider, key and model you configure in the chat screen." to
        "单词发音优先使用 LingQ 音频，失败时回退系统语音。AI 导师使用你在聊天页配置的服务商、密钥和模型。",
    "Account and profile" to "账号与资料",
    "Sign-in uses your LingQ account. The name and photo shown on the home screen are local customisations and do not change your LingQ profile." to
        "登录使用你的 LingQ 账号。首页显示的名字和头像只在本机生效，不会修改 LingQ 资料。",
    "Reminders" to "提醒",
    "Daily reading reminders are scheduled on this device. You can change the time or turn them off at any time in Settings." to
        "每日阅读提醒在本机调度，可随时在设置里修改时间或关闭。",

    // AI 聊天
    "Connect your own AI endpoint" to "接入你自己的 AI 接口",
    "Ask your AI anything about your target language." to "向 AI 提问任何和目标语言有关的问题。",
    "Explain a sentence from my lesson" to "解释我课程里的一句话",
    "Give me three example sentences" to "给我三个例句",
    "Correct my writing" to "修改我的写作",
    "Quiz me on my Inputa words" to "用我的 Inputa 单词考我",
    "Works with any OpenAI-compatible /chat/completions endpoint: OpenAI, DeepSeek, Ollama and one-api are all supported." to
        "支持任何 OpenAI 兼容的 /chat/completions 接口：OpenAI、DeepSeek、Ollama、one-api 都可以。",
    "Base URL" to "Base URL",
    "API Key" to "API Key",
    "Save AI settings" to "保存 AI 设置",
    "Chat with your AI…" to "和 AI 聊天…",
    "AI settings" to "AI 设置",
    "AI request failed" to "AI 请求失败",
    "The AI returned no content" to "AI 没有返回内容",
    "Fill in Base URL, API Key and Model in AI settings first." to "请先在 AI 设置里填写 Base URL、API Key 和 Model。",
    "Base URL, API Key and Model are all required" to "Base URL、API Key、Model 都需要填写",
    "Configure your own AI endpoint first" to "先配置你自己的 AI 接口",
    "The AI response was not JSON" to "AI 返回的不是 JSON",
    "The AI response contained no text" to "AI 响应里没有文本",
    "Invalid API key or no permission" to "API Key 无效或无权限",
    "Base URL is wrong; /chat/completions was not found" to "Base URL 不对，找不到 /chat/completions",
    "Too many requests" to "请求太频繁",
    "AI service is temporarily unavailable" to "AI 服务暂时不可用",
    "LingQ service is temporarily unavailable" to "LingQ 服务暂时不可用",
    "Token is invalid or expired" to "Token 无效或已过期",
    "The response was not valid JSON" to "响应不是合法 JSON",
    "Invalid API address" to "接口地址无效",
    "You do not have permission to access this content" to "没有权限访问该内容",
    "No lesson selected" to "没有选中的课程",
    "The login response did not contain a token" to "登录响应里没有 token",
    "Sign-in failed: invalid credentials" to "登录失败：凭据无效",
    "Your session expired. Sign in again." to "登录已过期，请重新登录。",
    "Paste your LingQ token" to "请粘贴 LingQ Token",
    "Incorrect username or password" to "账号或密码不正确",
    "Account is restricted" to "账号被限制",
    "Unknown" to "未知",

    // 导入与播放列表
    "Collection title" to "合集名称",
    "Lesson title" to "课程标题",
    "Source URL (optional)" to "来源链接（可选）",
    "Give the lesson a title." to "给课程起个标题。",
    "Paste an article URL or some text." to "粘贴文章链接或文本。",
    "Paste a URL or some text. LingQ splits and stores the lesson on your account." to
        "粘贴链接或文本，LingQ 会拆分并保存到你的账号。",
    "Paste the article or any text you want to read…" to "粘贴文章或想读的文本…",
    "Importing…" to "正在导入…",
    "Import started but LingQ didn't return the lesson yet. Check your library shortly." to
        "已开始导入，LingQ 还没返回课程，稍后去资料库看看。",
    "Add lessons from a lesson preview. They stay on this device and play from LingQ's audio." to
        "在课程预览里添加课程。列表保存在本机，音频来自 LingQ。",
    "Your playlist is empty" to "播放列表是空的",
    "No lessons queued" to "队列里没有课程",
    "Clear" to "清空",
    "Browse lessons" to "浏览课程",
    "No lessons returned" to "没有返回课程",
    "Course" to "课程",
    "Title" to "标题",
    "Text" to "文本",
    "Words" to "单词",
    "Listening" to "收听",

    // 个人资料
    "Edit Profile" to "编辑资料",
    "Shown on your home screen" to "显示在首页",
    "Tap the photo to choose a new one" to "点击照片更换头像",
    "Display name" to "显示名称",
    "Your name" to "你的名字",
    "Change photo" to "更换头像",
)

private val ZH_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("^([\\d,]+) new words$") to "\$1 个生词",
    Regex("^([\\d,]+) new$") to "\$1 个生词",
    Regex("^([\\d,]+) words$") to "\$1 词",
    Regex("^([\\d,]+(?:\\.\\d+)?[KM]?) words tracked$") to "已记录 \$1 个词",
    Regex("^([\\d,]+(?:\\.\\d+)?[KM]?) words remaining$") to "还剩 \$1 个词",
    Regex("^([\\d,]+) lessons$") to "\$1 节课",
    Regex("^Lessons \\((\\d+)\\)$") to "课程（\$1）",
    Regex("^([\\d,]+) cards$") to "\$1 张卡片",
    Regex("^All ([\\d,]+) terms$") to "全部 \$1 个词",
    Regex("^([\\d,]+) terms$") to "\$1 个词",
    Regex("^Level (\\d+)$") to "等级 \$1",
    Regex("^Lv\\.(\\d+)$") to "\$1 级",
    Regex("^([\\d,]+) day streak$") to "连续 \$1 天",
    Regex("^Daily at (\\d{2}:\\d{2})$") to "每天 \$1",
    Regex("^Reminder set for (\\d{2}:\\d{2})\\.$") to "提醒时间 \$1。",
    Regex("^Today  ([\\d,]+) / ([\\d,]+)$") to "今天  \$1 / \$2",
    Regex("^You reviewed (\\d+) Inputa words\\.$") to "你复习了 \$1 个 Inputa 单词。",
    Regex("^Inputa level (.+)$") to "Inputa 等级 \$1",
    Regex("^([\\d,]+) min$") to "\$1 分钟",
    Regex("^([\\d,]+)m$") to "\$1分钟",
    Regex("^([\\d,]+)h ([\\d,]+)m$") to "\$1小时 \$2分钟",
    Regex("^Network error: (.+)$") to "网络错误：\$1",
    Regex("^AI request failed \\(HTTP (\\d+)\\)$") to "AI 请求失败（HTTP \$1）",
    Regex("^Sign-in failed \\(HTTP (\\d+)\\)$") to "登录失败（HTTP \$1）",
    Regex("^Verification failed \\(HTTP (\\d+)\\)$") to "验证失败（HTTP \$1）",
    Regex("^Request failed \\(HTTP (\\d+)\\)$") to "请求失败（HTTP \$1）",
)

fun AppLanguage.translate(text: String): String {
    if (this == AppLanguage.English || text.isBlank()) return text
    ZH[text]?.let { return it }
    for ((pattern, replacement) in ZH_PATTERNS) {
        if (pattern.matches(text)) return pattern.replace(text, replacement)
    }
    return text
}

/**
 * 全局本地化 Text：默认走英文，切到中文时按词典翻译。
 * 希腊字母/课程正文等动态内容不在词典里时原样显示。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = LocalAppLanguage.current.translate(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style,
    )
}

/**
 * 内容文本：课程标题、单词、释义、聊天消息等不能被界面词典翻译。
 * 与 Text 保持同样的常用参数，方便替换。
 */
@Composable
fun RawText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        style = style,
    )
}
