package com.ai.aijava.agent.enums;

public enum DocStatus {

    /** 已上传，待处理 */
    UPLOADED("已上传"),

    /** 解析切分向量化中 */
    PROCESSING("处理中"),

    /** 摄取完成，可被检索 */
    COMPLETED("已完成"),

    /** 摄取失败（只能删除重传，不做重处理接口） */
    FAILED("失败");


    /** 私有变量 */
    private final String label;

    /** 构造函数 */
    DocStatus(String label) {
        this.label = label;
    }

    /** 返回变量  @return label  */
    public String getLabel() {
        return label;
    }
}
