package com.ai.aijava.agent.pipeline;

import org.apache.tika.metadata.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
public class DocumentParser {

    /** Tika 提取文本上限（-1 表示不限制，由上传大小限制间接约束） */
    private static final int MAX_TEXT_LENGTH = -1;

    /**
     * 解析文件流为纯文本
     *
     * @param inputStream 文件输入流（调用方负责关闭）
     * @return 提取的纯文本
     * @throws Exception 解析失败（由摄取服务转为 FAILED 状态）
     */
    public String parse(InputStream inputStream) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        parser.parse(inputStream,handler,metadata);
        String text = handler.toString();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("文档解析结果为空（可能是扫描版 PDF 或空文档）");
        }
        return text.trim();
    }

}
