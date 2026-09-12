package com.ai.aijava.agent.entity;

import com.ai.aijava.agent.enums.DocStatus;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体，对应 knowledge_document 表
 * 摄取状态机由 DocumentIngestService 驱动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("knowledge_document")
public class KnowledgeDocument {

    /** 文档 ID（主键，自增） */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型（pdf/docx/md/txt） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 服务器存储路径 */
    private String filePath;

    /** 处理状态（DocStatus 枚举名，DB 存 String 便于排查） */
    private String status;

    /** 处理失败原因（FAILED 时） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 状态是否为指定枚举
     */
    public boolean isStatus(DocStatus docStatus) {
        return docStatus.name().equals(this.status);
    }
}
