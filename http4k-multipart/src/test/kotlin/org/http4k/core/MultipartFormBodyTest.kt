package org.http4k.core

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.lens.Header
import org.junit.jupiter.api.Test
import java.io.InputStream

class MultipartFormBodyTest {

    @Test
    fun `retreiving files and fields`() {
        val file1 = FormFile("foo.txt", TEXT_PLAIN, "content".byteInputStream())
        val file2 = FormFile("foo2.txt", TEXT_PLAIN, "content2".byteInputStream())
        val form = MultipartFormBody("bob") +
                ("field" to "foo") +
                ("field" to "bar") +
                ("file" to file1) +
                ("file" to file2)

        assertThat(form.field("field"), equalTo("foo"))
        assertThat(form.fields("field"), equalTo(listOf("foo", "bar")))

        assertThat(form.file("file"), equalTo(file1))
        assertThat(form.files("file"), equalTo(listOf(file1, file2)))
    }

    @Test
    fun roundtrip() {
        val form = MultipartFormBody("bob") + ("field" to "bar") +
                ("file" to FormFile("foo.txt", TEXT_PLAIN, "content".byteInputStream()))

        val req = Request(Method.POST, "")
                .with(Header.CONTENT_TYPE of ContentType.MultipartFormWithBoundary(form.boundary))
                .body(form)

        MultipartFormBody.from(req) shouldMatch equalTo(
                MultipartFormBody("bob") + ("field" to "bar") +
                        ("file" to FormFile("foo.txt", TEXT_PLAIN, "content".byteInputStream()))
        )
    }

    @Test
    fun `can handle when body is already pulled into memory`() {
        val form = MultipartFormBody("bob") + ("field" to "bar") +
                ("file" to FormFile("foo.txt", TEXT_PLAIN, "content".byteInputStream()))

        val req = Request(Method.POST, "")
                .with(Header.CONTENT_TYPE of ContentType.MultipartFormWithBoundary(form.boundary))
                .body(form)

        req.bodyString()

        MultipartFormBody.from(req) shouldMatch equalTo(
                MultipartFormBody("bob") + ("field" to "bar") +
                        ("file" to FormFile("foo.txt", TEXT_PLAIN, "content".byteInputStream()))
        )
    }

    @Test
    fun `closing streams - manually created multipart`() {
        val streams = (1..3).map { "content $it" }.map { TestInputStream(it) }

        val body = streams.toMultipartForm()

        streams shouldMatch open

        body.close()

        streams shouldMatch closed
    }

    @Test
    fun `closing streams - manually created multipart can be closed via its stream`() {
        val streams = (1..3).map { "content $it" }.map { TestInputStream(it) }

        val body = streams.toMultipartForm()

        streams shouldMatch open

        body.stream.close()

        streams shouldMatch closed
    }

    @Test
    fun `closing streams - parsed from existing message`() {
        val streams = (1..3).map { "content $it" }.map { TestInputStream(it) }

        val original = streams.toMultipartForm()

        MultipartFormBody.from(Request(Method.POST, "/")
                .with(Header.CONTENT_TYPE of ContentType.MultipartFormWithBoundary(original.boundary))
                .body(original))

        streams shouldMatch closed //original stream are automatically closed during parsing
    }

    private fun List<TestInputStream>.toMultipartForm() =
            foldIndexed(MultipartFormBody())
            { index, acc, stream -> acc.plus("file$index" to FormFile("foo$index.txt", TEXT_PLAIN, stream)) }

    private class TestInputStream(private var text: String) : InputStream() {
        private val stream = text.byteInputStream()
        var closed: Boolean = false

        override fun read(): Int = stream.read()

        override fun close() {
            super.close()
            closed = true
        }

        override fun toString(): String = "($closed) ${text.substring(0, 9)}"
    }

    private val isClosed = Matcher(TestInputStream::closed)
    private val open = allElements(!isClosed)
    private val closed = allElements(isClosed)
}
