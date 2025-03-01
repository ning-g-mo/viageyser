package cn.ningmo.viageyser;

import org.bukkit.Bukkit;
import org.geysermc.geyser.GeyserImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class ProtocolVersionHook {
    private final Logger logger;
    private int minProtocolVersion = 400; // 默认值
    private int maxProtocolVersion = -1;  // 默认不限制
    private boolean debug = false;

    public ProtocolVersionHook(Logger logger) {
        this.logger = logger;
    }

    public void setMinProtocolVersion(int minProtocolVersion) {
        this.minProtocolVersion = minProtocolVersion;
    }

    public void setMaxProtocolVersion(int maxProtocolVersion) {
        this.maxProtocolVersion = maxProtocolVersion;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean applyHook() {
        try {
            // 尝试获取 Geyser 实例
            GeyserImpl geyser = GeyserImpl.getInstance();
            if (geyser == null) {
                logger.warning("无法获取 Geyser 实例，请确保 Geyser 已正确加载");
                return false;
            }

            if (debug) {
                logger.info("开始应用协议版本钩子...");
            }

            // 尝试多种可能的字段名称和类路径
            boolean success = false;
            
            // 首先尝试修改连接处理器中的版本检查
            success = tryModifyConnectionHandler() || success;
            
            // 尝试修改 BedrockProtocol 类中的协议版本常量
            success = tryModifyBedrockProtocol() || success;
            
            // 尝试修改会话管理器中的版本检查逻辑
            success = tryModifySessionManager(geyser) || success;
            
            return success;
        } catch (Exception e) {
            logger.severe("应用协议版本钩子时发生错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean tryModifyBedrockProtocol() {
        try {
            // 尝试找到可能的类路径
            String[] possibleClassPaths = {
                "org.geysermc.geyser.network.BedrockProtocol",
                "org.geysermc.geyser.translator.protocol.BedrockProtocol",
                "org.geysermc.connector.network.BedrockProtocol",
                "org.geysermc.geyser.network.GameProtocol",
                "org.geysermc.geyser.translator.protocol.PacketTranslator",
                "org.geysermc.connector.network.session.GeyserSession",
                "org.geysermc.geyser.session.GeyserSession",
                "org.geysermc.geyser.network.MinecraftProtocol",
                "org.geysermc.geyser.registry.type.MinecraftProtocol",
                "org.geysermc.geyser.level.BedrockProtocol",
                "org.geysermc.geyser.network.protocol.ProtocolVersion"
            };
            
            // 尝试查找类
            Class<?> bedrockProtocolClass = null;
            for (String path : possibleClassPaths) {
                try {
                    bedrockProtocolClass = Class.forName(path);
                    if (debug) {
                        logger.info("找到可能的协议类: " + path);
                    }
                    
                    // 尝试修改这个类中的版本字段
                    boolean modified = tryModifyClassFields(bedrockProtocolClass);
                    if (modified) {
                        logger.info("成功修改 " + path + " 中的版本信息");
                        return true;
                    }
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个路径
                } catch (Exception e) {
                    if (debug) {
                        logger.warning("尝试修改 " + path + " 时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            // 如果没有找到任何类，尝试直接查找和修改 Geyser 实例中的字段
            if (debug) {
                logger.info("尝试直接在 Geyser 实例中查找协议版本字段");
            }
            
            GeyserImpl geyser = GeyserImpl.getInstance();
            if (geyser != null) {
                boolean modified = tryModifyGeyserFields(geyser);
                if (modified) {
                    return true;
                }
            }
            
            logger.warning("无法找到或修改任何协议版本相关的类或字段");
            return false;
        } catch (Exception e) {
            logger.warning("修改 BedrockProtocol 失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private boolean tryModifyClassFields(Class<?> clazz) throws Exception {
        boolean modified = false;
        
        // 尝试修改各种可能的字段名
        String[] versionFields = {
            "MINIMUM_PROTOCOL_VERSION", "MIN_PROTOCOL_VERSION", 
            "BEDROCK_PROTOCOL_VERSION", "PROTOCOL_VERSION",
            "CURRENT_PROTOCOL_VERSION", "SUPPORTED_PROTOCOL_VERSION",
            "LATEST_PROTOCOL_VERSION", "BEDROCK_MINIMUM_VERSION",
            "MINIMUM_VERSION", "LOWEST_PROTOCOL_VERSION"
        };
        
        // 获取类中的所有字段
        Field[] allFields = clazz.getDeclaredFields();
        if (debug) {
            logger.info("类 " + clazz.getName() + " 中的字段:");
            for (Field field : allFields) {
                logger.info("  - " + field.getName() + " (" + field.getType().getName() + ")");
            }
        }
        
        // 尝试修改指定名称的字段
        for (String fieldName : versionFields) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                if (field.getType() == int.class || field.getType() == Integer.class) {
                    modified = modifyStaticIntField(field, minProtocolVersion) || modified;
                    if (debug && modified) {
                        logger.info("成功修改字段 " + fieldName + " 为 " + minProtocolVersion);
                    }
                }
            } catch (NoSuchFieldException ignored) {
                // 继续尝试下一个字段
            }
        }
        
        // 如果设置了最大协议版本限制
        if (maxProtocolVersion > 0) {
            String[] maxVersionFields = {
                "MAXIMUM_PROTOCOL_VERSION", "MAX_PROTOCOL_VERSION",
                "HIGHEST_PROTOCOL_VERSION", "LATEST_PROTOCOL_VERSION"
            };
            
            for (String fieldName : maxVersionFields) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    if (field.getType() == int.class || field.getType() == Integer.class) {
                        modified = modifyStaticIntField(field, maxProtocolVersion) || modified;
                        if (debug && modified) {
                            logger.info("成功修改字段 " + fieldName + " 为 " + maxProtocolVersion);
                        }
                    }
                } catch (NoSuchFieldException ignored) {
                    // 继续尝试下一个字段
                }
            }
        }
        
        // 尝试查找和修改任何看起来像协议版本的整数常量字段
        for (Field field : allFields) {
            String name = field.getName().toUpperCase();
            if ((name.contains("PROTOCOL") || name.contains("VERSION")) && 
                (field.getType() == int.class || field.getType() == Integer.class)) {
                try {
                    field.setAccessible(true);
                    int value = field.getInt(null);
                    
                    // 如果字段名包含 MIN 或 MINIMUM，并且值大于我们的最小值
                    if ((name.contains("MIN") || name.contains("MINIMUM") || name.contains("LOWEST")) && value > minProtocolVersion) {
                        modified = modifyStaticIntField(field, minProtocolVersion) || modified;
                        if (debug) {
                            logger.info("修改疑似最小版本字段 " + field.getName() + " 从 " + value + " 到 " + minProtocolVersion);
                        }
                    }
                    
                    // 如果字段名包含 MAX 或 MAXIMUM，并且我们设置了最大值限制
                    if (maxProtocolVersion > 0 && (name.contains("MAX") || name.contains("MAXIMUM") || name.contains("HIGHEST")) && value < maxProtocolVersion) {
                        modified = modifyStaticIntField(field, maxProtocolVersion) || modified;
                        if (debug) {
                            logger.info("修改疑似最大版本字段 " + field.getName() + " 从 " + value + " 到 " + maxProtocolVersion);
                        }
                    }
                } catch (Exception e) {
                    if (debug) {
                        logger.warning("尝试修改字段 " + field.getName() + " 时出错: " + e.getMessage());
                    }
                }
            }
        }
        
        return modified;
    }
    
    private boolean tryModifyGeyserFields(GeyserImpl geyser) {
        boolean modified = false;
        
        try {
            // 尝试获取 Geyser 实例中的所有字段
            Field[] fields = geyser.getClass().getDeclaredFields();
            
            for (Field field : fields) {
                field.setAccessible(true);
                String name = field.getName().toLowerCase();
                
                // 查找可能包含协议信息的字段
                if (name.contains("protocol") || name.contains("version") || name.contains("translator")) {
                    Object fieldValue = field.get(geyser);
                    if (fieldValue != null) {
                        if (debug) {
                            logger.info("检查 Geyser 字段: " + field.getName() + " (" + fieldValue.getClass().getName() + ")");
                        }
                        
                        // 递归检查这个字段中的所有字段
                        modified = tryModifyClassFields(fieldValue.getClass()) || modified;
                        
                        // 如果这个字段是一个对象，尝试修改它的字段
                        if (!fieldValue.getClass().isPrimitive() && !fieldValue.getClass().getName().startsWith("java.lang")) {
                            Field[] subFields = fieldValue.getClass().getDeclaredFields();
                            for (Field subField : subFields) {
                                String subName = subField.getName().toUpperCase();
                                if ((subName.contains("PROTOCOL") || subName.contains("VERSION")) && 
                                    (subField.getType() == int.class || subField.getType() == Integer.class)) {
                                    subField.setAccessible(true);
                                    int value = subField.getInt(fieldValue);
                                    
                                    if (debug) {
                                        logger.info("  - 子字段: " + subField.getName() + " = " + value);
                                    }
                                    
                                    // 尝试修改这个子字段
                                    if (subName.contains("MIN") && value > minProtocolVersion) {
                                        subField.set(fieldValue, minProtocolVersion);
                                        modified = true;
                                        logger.info("修改 " + field.getName() + "." + subField.getName() + " 从 " + value + " 到 " + minProtocolVersion);
                                    }
                                    
                                    if (maxProtocolVersion > 0 && subName.contains("MAX") && value < maxProtocolVersion) {
                                        subField.set(fieldValue, maxProtocolVersion);
                                        modified = true;
                                        logger.info("修改 " + field.getName() + "." + subField.getName() + " 从 " + value + " 到 " + maxProtocolVersion);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (debug) {
                logger.warning("尝试修改 Geyser 实例字段时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return modified;
    }
    
    private boolean modifyStaticIntField(Field field, int newValue) throws Exception {
        field.setAccessible(true);
        
        // 获取当前值
        int currentValue = field.getInt(null);
        if (debug) {
            logger.info("字段 " + field.getName() + " 当前值: " + currentValue);
        }
        
        // 移除 final 修饰符
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        } catch (NoSuchFieldException e) {
            // Java 9+ 不支持直接修改 modifiers 字段
            // 尝试使用 VarHandle 或其他方法
            if (debug) {
                logger.info("无法移除 final 修饰符，可能是 Java 9+ 环境");
            }
        }
        
        // 设置新值
        field.set(null, newValue);
        
        // 验证修改是否成功
        int updatedValue = field.getInt(null);
        return updatedValue == newValue;
    }
    
    private boolean tryModifySessionManager(GeyserImpl geyser) {
        try {
            // 获取会话管理器
            Object sessionManager = getFieldValue(geyser, "sessionManager");
            if (sessionManager == null) {
                logger.warning("无法获取会话管理器");
                return false;
            }
            
            // 由于我们无法直接修改会话管理器中的方法，这里我们只记录找到了会话管理器
            if (debug) {
                logger.info("找到会话管理器: " + sessionManager.getClass().getName());
            }
            
            // 目前我们不实际修改会话管理器，因为这需要字节码操作
            // 返回 false 表示此方法未成功修改任何内容
            return false;
        } catch (Exception e) {
            logger.warning("修改会话管理器失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private boolean tryModifyConnectionHandler() {
        try {
            // 尝试找到连接处理器类
            String[] possibleHandlerClasses = {
                "org.geysermc.geyser.network.UpstreamPacketHandler",
                "org.geysermc.connector.network.UpstreamPacketHandler",
                "org.geysermc.geyser.network.session.UpstreamPacketHandler"
            };
            
            Class<?> handlerClass = null;
            for (String className : possibleHandlerClasses) {
                try {
                    handlerClass = Class.forName(className);
                    if (debug) {
                        logger.info("找到连接处理器类: " + className);
                    }
                    break;
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个类名
                }
            }
            
            if (handlerClass == null) {
                logger.warning("无法找到连接处理器类");
                return false;
            }
            
            // 尝试查找和修改 BedrockCodec 类
            boolean modified = tryModifyBedrockCodec();
            if (modified) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.warning("修改连接处理器失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private boolean tryModifyBedrockCodec() {
        try {
            // 尝试找到 BedrockCodec 类
            String[] possibleCodecClasses = {
                "org.cloudburstmc.protocol.bedrock.codec.BedrockCodec",
                "org.geysermc.connector.network.BedrockCodec",
                "org.geysermc.geyser.network.BedrockCodec",
                "com.nukkitx.protocol.bedrock.BedrockCodec"
            };
            
            Class<?> codecClass = null;
            for (String className : possibleCodecClasses) {
                try {
                    codecClass = Class.forName(className);
                    if (debug) {
                        logger.info("找到 BedrockCodec 类: " + className);
                    }
                    break;
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个类名
                }
            }
            
            if (codecClass == null) {
                logger.warning("无法找到 BedrockCodec 类");
                return false;
            }
            
            // 尝试获取 CODEC_LOOKUP 字段
            Field lookupField = null;
            String[] possibleLookupFields = {
                "CODEC_LOOKUP", "SUPPORTED_CODECS", "CODECS", "CODEC_BY_VERSION"
            };
            
            for (String fieldName : possibleLookupFields) {
                try {
                    lookupField = codecClass.getDeclaredField(fieldName);
                    if (debug) {
                        logger.info("找到 CODEC_LOOKUP 字段: " + fieldName);
                    }
                    break;
                } catch (NoSuchFieldException ignored) {
                    // 继续尝试下一个字段名
                }
            }
            
            if (lookupField == null) {
                // 尝试查找任何看起来像 Map 的静态字段
                for (Field field : codecClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && 
                        (field.getType() == java.util.Map.class || 
                         field.getType().getName().contains("Map") || 
                         field.getType().getName().contains("Collection"))) {
                        lookupField = field;
                        if (debug) {
                            logger.info("找到可能的 CODEC_LOOKUP 字段: " + field.getName());
                        }
                        break;
                    }
                }
            }
            
            if (lookupField == null) {
                logger.warning("无法找到 CODEC_LOOKUP 字段");
                return false;
            }
            
            // 尝试修改 CODEC_LOOKUP 字段
            lookupField.setAccessible(true);
            Object lookup = lookupField.get(null);
            
            if (lookup instanceof java.util.Map) {
                // 使用原始类型避免泛型转换问题
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) lookup;
                
                if (debug) {
                    logger.info("CODEC_LOOKUP 是一个 Map，包含 " + map.size() + " 个条目");
                    for (Object key : map.keySet()) {
                        logger.info("  - 协议版本: " + key);
                    }
                }
                
                // 尝试找到最新的 codec
                Object latestCodec = null;
                Integer latestVersion = null;
                for (Object key : map.keySet()) {
                    if (key instanceof Integer) {
                        int version = (Integer) key;
                        if (latestVersion == null || version > latestVersion) {
                            latestVersion = version;
                            latestCodec = map.get(key);
                        }
                    }
                }
                
                if (latestCodec != null && latestVersion != null) {
                    // 为低版本添加相同的 codec
                    boolean modified = false;
                    for (int version = minProtocolVersion; version < latestVersion; version++) {
                        if (!map.containsKey(version)) {
                            map.put(version, latestCodec);
                            if (debug) {
                                logger.info("添加协议版本 " + version + " 的支持");
                            }
                            modified = true;
                        }
                    }
                    
                    if (modified) {
                        logger.info("成功修改 BedrockCodec 的 CODEC_LOOKUP，添加了对低版本的支持");
                        return true;
                    }
                }
            }
            
            // 尝试查找和修改 GameProtocol 类
            return tryModifyGameProtocol();
        } catch (Exception e) {
            logger.warning("修改 BedrockCodec 失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private boolean tryModifyGameProtocol() {
        try {
            Class<?> gameProtocolClass = Class.forName("org.geysermc.geyser.network.GameProtocol");
            if (debug) {
                logger.info("找到 GameProtocol 类");
            }
            
            // 尝试获取 SUPPORTED_BEDROCK_CODECS 字段
            Field codecsField = gameProtocolClass.getDeclaredField("SUPPORTED_BEDROCK_CODECS");
            codecsField.setAccessible(true);
            
            Object codecs = codecsField.get(null);
            if (codecs instanceof java.util.List) {
                // 使用原始类型避免泛型转换问题
                @SuppressWarnings("unchecked")
                java.util.List<Object> codecsList = (java.util.List<Object>) codecs;
                
                if (debug) {
                    logger.info("SUPPORTED_BEDROCK_CODECS 是一个 List，包含 " + codecsList.size() + " 个条目");
                }
                
                // 尝试获取 BedrockCodec 类
                Class<?> bedrockCodecClass = Class.forName("org.cloudburstmc.protocol.bedrock.codec.BedrockCodec");
                
                // 尝试获取 builder 方法
                java.lang.reflect.Method builderMethod = bedrockCodecClass.getMethod("builder");
                Object builder = builderMethod.invoke(null);
                
                // 尝试获取 protocolVersion 方法
                java.lang.reflect.Method protocolVersionMethod = builder.getClass().getMethod("protocolVersion", int.class);
                
                // 尝试获取 build 方法
                java.lang.reflect.Method buildMethod = builder.getClass().getMethod("build");
                
                // 创建新的 codec 并添加到列表中
                boolean modified = false;
                for (int version = minProtocolVersion; version < 600; version++) {
                    // 检查这个版本是否已经存在
                    boolean exists = false;
                    for (Object codec : codecsList) {
                        java.lang.reflect.Method getProtocolVersionMethod = codec.getClass().getMethod("getProtocolVersion");
                        int codecVersion = (int) getProtocolVersionMethod.invoke(codec);
                        if (codecVersion == version) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        // 创建新的 codec
                        protocolVersionMethod.invoke(builder, version);
                        Object newCodec = buildMethod.invoke(builder);
                        
                        // 添加到列表中
                        codecsList.add(newCodec);
                        if (debug) {
                            logger.info("添加协议版本 " + version + " 的支持");
                        }
                        modified = true;
                    }
                }
                
                if (modified) {
                    logger.info("成功修改 GameProtocol 的 SUPPORTED_BEDROCK_CODECS，添加了对低版本的支持");
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            logger.warning("修改 GameProtocol 失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
} 