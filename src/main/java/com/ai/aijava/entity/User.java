package com.ai.aijava.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MyBatis-Flex 大厂标准配置方式
 * 不在每个实体写注解逐个配置，统一全局接管：
 * java
 * 运行
 * @Configuration
 * public class FlexConfig {
 *     static {
 *         // 全局默认主键生成策略：雪花
 *         FlexGlobalConfig.getKeyGenerator()
 *                 .setGenerator(new SnowflakeKeyGenerator());
 *     }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String phone;

    private String loginIp;

    private LocalDateTime loginTime;

    private Integer loginFailCount;

    private LocalDateTime lockTime;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
