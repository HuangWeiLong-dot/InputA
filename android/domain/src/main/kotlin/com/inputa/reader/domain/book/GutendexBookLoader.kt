package com.inputa.reader.domain.book

import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.BookSource
import kotlinx.coroutines.CancellationException

/**
 * 下载一条 URL 的正文。实现里要处理「非 2xx」「空正文」「返回的是 HTML 页面」
 * 这几种失败，并抛出带**可读消息**的 [BookDownloadException] —— 消息会原样拼进
 * 最终给用户看的那句「加载读物失败：…」里。
 */
fun interface TextDownloader {
    suspend fun download(url: String): String
}

/** 下载或解析读物失败。消息是面向用户的中文。 */
class BookDownloadException(message: String) : Exception(message)

/**
 * 把一条 Gutendex 书目记录变成一本可读的书：挑纯文本链接 → 逐个下载 →
 * 切章。第一个成功的就返回，全部失败时抛一句带上每次失败原因的错。
 *
 * 与词典链同样的形态：纯逻辑在这里，HTTP 由 [TextDownloader] 注入，于是
 * 「先试哪个链接」「空正文怎么办」这些规则能在纯 JVM 上测。
 */
class GutendexBookLoader(private val downloader: TextDownloader) {

    suspend fun load(item: GutendexBook): Book {
        val urls = PlainTextUrls.collect(item)
        if (urls.isEmpty()) {
            throw BookDownloadException("该书未提供纯文本（text/plain）格式，无法在阅读器中打开。")
        }

        val failures = ArrayList<String>()

        for (url in urls) {
            try {
                val rawText = downloader.download(url)
                val chapters = GutenbergTextProcessor.process(rawText, item.title)

                if (chapters.all { it.content.trim().isEmpty() }) {
                    failures += "$url 正文为空"
                    continue
                }

                return Book(
                    id = "gutendex-${item.id}",
                    title = item.title,
                    author = item.authors.joinToString(", ").ifEmpty { "Unknown Author" },
                    coverUrl = item.formats["image/jpeg"],
                    chapters = chapters,
                    source = BookSource.GUTENBERG,
                    // 书源元数据优先；缺失时由导入方对正文跑一次语言检测。
                    language = item.languages.firstOrNull(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                failures += error.message ?: "下载失败"
            }
        }

        throw BookDownloadException("加载读物失败：${failures.joinToString("；")}")
    }
}
