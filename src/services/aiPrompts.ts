/**
 * 默认 AI 提示词。
 *
 * 逐字移植自 LingKuma（MIT，见仓库根 NOTICE.md）的默认值：源文件是
 * `LingKuma/src/options/options.js`（可编辑的默认值）与
 * `LingKuma/src/service/a3_aiFragen.js`（同文的硬编码兜底）。
 *
 * LingKuma 允许用户覆盖这些提示词，并且用 `.replace('{word}', word)` 展开占位符 ——
 * 注意那只替换**第一个**匹配，而它的 `aiPrompt` 里 `{word}` 出现多次，所以那条
 * 自定义路径其实是坏的。这里改成模板字符串直接插值：默认值本来就只读，
 * 不存在「只替换一次」的问题。
 *
 * 统一签名 `(word, sentence) => string`，方便调用方无差别使用。
 */

/**
 * AI 推荐笔记 ①：翻译单词或短语。
 *
 * 输出刻意被约束成「只有译文」——它会被存进用户的笔记，任何前缀标签、
 * 分析或引号都是噪声。固定短语走「完整短语: 中文翻译」两段式。
 */
export function wordNotePrompt(word: string, sentence: string): string {
  return `
# 角色
你是翻译专家，根据上下文判断单词或短语并翻译。

# 输出规则（严格执行）

**情况一：固定短语**
若 ${word} 在句中构成固定短语/习语，输出格式为：
"
完整短语: 中文翻译
"
示例："break the ice: 打破僵局"

**情况二：独立单词**
若 ${word} 只是独立单词，输出格式为：
"
中文翻译
"
示例："打破"

# 禁止事项
- 禁止输出"单词："、"英文："、"翻译："、"中文翻译："等任何前缀标签
- 禁止输出分析、解释、语法说明
- 禁止输出引号
- 只输出翻译结果，别的什么都不要说

# 任务
判断句子 ${sentence}中，${word} 是独立单词还是固定短语的一部分，按上述格式输出翻译。
`;
}

/**
 * AI 推荐笔记 ②：语法精要。
 *
 * 与 ① 的分工是「一个管意思、一个管用法」。刻意要求 20 字左右 ——
 * 笔记栏位窄，长篇语法分析属于「AI 拆解语法」那条路。
 */
export function grammarGlossPrompt(word: string, sentence: string): string {
  return `
# 角色
你是一位精通德语 日语 英语的语法解析专家，擅长根据上下文精确判断对应单词的解析精要

# 任务
根据提供的 [句子]，判断 [待解析词] 在该语境下的具体语法作用，形变规则等

# 核心规则
返回20字左右精要解析。

# 输入
句子：'${sentence}'
待解析词：'${word}'

# 输出格式
直接返回解析内容
`;
}

/**
 * 例句翻译：整句译文，并把目标词对应的中文部分用 Markdown 加粗。
 * 加粗标记由渲染层解析成 `<strong>`，所以这里要求的是 Markdown 而不是 HTML。
 */
export function sentenceTranslationPrompt(word: string, sentence: string): string {
  return `请将句子: '${sentence}'翻译为中文，并将句子中单词"'${word}'"对应的中文的部分用Markdown加粗显示。只返回翻译结果，不要额外说明。`;
}

/**
 * 语法拆解。
 *
 * 移植时保留了这个德语长句的 few-shot 示例：它示范的正是想要的输出形状
 * （「直译：… 解析：… 逐词成分」），比任何形容词描述都管用。末尾一句把
 * 任务交出去，所以 `{sentence}` 必须留在最后。
 */
export function sentenceAnalysisPrompt(sentence: string): string {
  return `直译： 我敬畏地观察着两位可怕的战士一次又一次地交叉他们的剑。 解析： 1.- Ich beobachte ehrfürchtig: "我敬畏地观察"。   - Ich: "我"，主语。   - beobachte: "观察"，动词"beobachten"的第一人称单数形式。   - ehrfürchtig: "敬畏地"，副词，表示对某事物的尊敬或畏惧。 2.- wie die beiden furchterregenden Krieger immer wieder ihre Klingen kreuzen: "两位可怕的战士一次又一次地交叉他们的剑"。   - wie: "如何"，引导方式状语从句。   - die beiden furchterregenden Krieger: "这两位可怕的战士"。     - die beiden: "这两位"，指示代词。     - furchterregenden: "可怕的"，形容词，表示"令人恐惧"。     - Krieger: "战士"，名词，指战斗者。   - immer wieder: "一次又一次"，副词短语，表示重复发生。   - ihre Klingen kreuzen: "交叉他们的剑"。      - ihre: "他们的"，物主代词。     - Klingen: "剑"，名词，表示剑或刀刃。     - kreuzen: "交叉"，动词，表示交叉或交锋。 借鉴上面解析格式，用中文解析下列英语/德语等其他语言的句子: ${sentence}`;
}

/** 拆解之后继续追问。`history` 是已经拼好的对话记录文本。 */
export function chatPrompt(sentence: string, history: string, question: string): string {
  return `请根据以下句子和对话历史回答用户的问题：\n\n句子：${sentence}\n\n对话历史：${history}\n\n用户问题：${question}`;
}

/** 追问模式的 system 提示：把当前句子固定进上下文。 */
export function chatSystemPrompt(sentence: string): string {
  return `你是一个语言学习助手，专门帮助用户理解句子中的单词和语法。当前句子是：${sentence}`;
}

/** 把对话记录拼成提示词里 `{history}` 那一段的文本。 */
export function formatConversationHistory(
  messages: Array<{ role: string; content: string }>,
): string {
  return messages.map((msg) => `${msg.role === 'user' ? '用户' : 'AI'}: ${msg.content}`).join('\n');
}
