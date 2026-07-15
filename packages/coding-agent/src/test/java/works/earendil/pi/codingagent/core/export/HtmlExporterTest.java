package works.earendil.pi.codingagent.core.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void escapesDynamicSessionFieldsAndRendersStructuralBlocks() throws Exception {
        Path session = tempDir.resolve("session.jsonl");
        Path output = tempDir.resolve("export.html");
        Files.write(session, List.of(
                """
                {"type":"session","version":3,"id":"xss","timestamp":"2026-07-04T00:00:00Z","cwd":"/tmp"}
                """.trim(),
                """
                {"type":"message","id":"1","parentId":null,"timestamp":"2026-07-04T00:00:01Z","message":{"role":"user","source":"extension\\"><script>alert(11)</script>","content":[{"type":"text","text":"<skill name=\\"demo&amp;docs\\" location=\\"/tmp/SKILL.md\\">\\nUse <b>markdown</b> safely.\\n</skill>\\n\\nFix <script>alert(2)</script> now"}]}}
                """.trim(),
                """
                {"type":"message","id":"2","parentId":"1","timestamp":"2026-07-04T00:00:02Z","message":{"role":"assistant<script>alert(1)</script>","content":[{"type":"text","text":"role should be escaped"}]}}
                """.trim(),
                """
                {"type":"message","id":"3","parentId":"2","timestamp":"2026-07-04T00:00:03Z","message":{"role":"assistant","content":[{"type":"toolCall","name":"edit\\"><img src=x onerror=alert(3)>","input":{"path":"<bad>.java","oldText":"a","newText":"b"}},{"type":"tool_result","content":[{"type":"text","text":"done <script>alert(4)</script>"}]}]}}
                """.trim(),
                """
                {"type":"custom","id":"4","parentId":"3","timestamp":"2026-07-04T00:00:04Z","customType":"skill_trigger_diagnostics\\"><svg onload=alert(5)>","data":{"matches":[{"skill":"demo<script>alert(6)</script>","path":"/tmp/SKILL.md"}]}}
                """.trim(),
                """
                {"type":"custom_message","id":"4b","parentId":"4","timestamp":"2026-07-04T00:00:04Z","customType":"extension.context","content":{"note":"from extension"},"display":true,"details":null,"source":"extension\\"><script>alert(10)</script>"}
                """.trim(),
                """
                {"type":"message","id":"5","parentId":"4b","timestamp":"2026-07-04T00:00:05Z","message":{"role":"assistant","content":[{"type":"image","mimeType":"image/png","data":"aW1hZ2U="},{"type":"image","mimeType":"image/jpeg","url":"https://example.com/image.jpg?x=1\\" onerror=\\"alert(7)"},{"type":"image","mimeType":"image/svg+xml","data":"PHN2ZyBvbmxvYWQ9YWxlcnQoOCk+"},{"type":"image","mimeType":"image/png","url":"javascript:alert(9)"}]}}
                """.trim()
        ));

        HtmlExporter.exportToFile(session, output);

        String html = Files.readString(output);
        assertThat(html)
                .contains("Skill: <strong>demo&amp;docs</strong>")
                .contains("ASSISTANT&lt;SCRIPT&gt;ALERT(1)&lt;/SCRIPT&gt;")
                .contains("Fix &lt;script&gt;alert(2)&lt;/script&gt; now")
                .contains("Tool Use: <strong>edit&quot;&gt;&lt;img src=x onerror=alert(3)&gt;</strong>")
                .contains("done &lt;script&gt;alert(4)&lt;/script&gt;")
                .contains("Custom Entry: <strong>skill_trigger_diagnostics&quot;&gt;&lt;svg onload=alert(5)&gt;</strong>")
                .contains("Custom Entry: <strong>extension.context</strong>")
                .contains("source=extension&quot;&gt;&lt;script&gt;alert(10)&lt;/script&gt;")
                .contains("source=extension&quot;&gt;&lt;script&gt;alert(11)&lt;/script&gt;")
                .contains("from extension")
                .contains("demo&lt;script&gt;alert(6)&lt;/script&gt;")
                .contains("src=\"data:image/png;base64,aW1hZ2U=\"")
                .contains("src=\"https://example.com/image.jpg?x=1&quot; onerror=&quot;alert(7)\"")
                .contains("Image omitted: unsupported inline image mime type")
                .contains("Image omitted: unsupported image URL scheme");
        assertThat(html)
                .doesNotContain("<script>alert")
                .doesNotContain("source=extension\"><script>")
                .doesNotContain("<img src=x")
                .doesNotContain("<svg onload")
                .doesNotContain("data:image/svg+xml")
                .doesNotContain("src=\"javascript:")
                .doesNotContain("&lt;skill name=");
    }
}
