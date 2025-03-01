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
            
            // 尝试修改 BedrockProtocol 类中的协议版本常量
            success = tryModifyBedrockProtocol() || success;
            
            // 尝试修改会话管理器中的版本检查逻辑
            success = tryModifySessionManager(geyser) || success;
            
            // 尝试修改连接处理器中的版本检查
            success = tryModifyConnectionHandler() || success;
            
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
                "org.geysermc.connector.network.BedrockProtocol"
            };
            
            Class<?> bedrockProtocolClass = null;
            for (String path : possibleClassPaths) {
                try {
                    bedrockProtocolClass = Class.forName(path);
                    if (debug) {
                        logger.info("找到 BedrockProtocol 类: " + path);
                    }
                    break;
                } catch (ClassNotFoundException ignored) {
                    // 继续尝试下一个路径
                }
            }
            
            if (bedrockProtocolClass == null) {
                logger.warning("无法找到 BedrockProtocol 类");
                return false;
            }
            
            // 尝试修改各种可能的字段
            boolean modified = false;
            
            // 尝试修改最小协议版本字段
            String[] minVersionFields = {"MINIMUM_PROTOCOL_VERSION", "MIN_PROTOCOL_VERSION"};
            for (String fieldName : minVersionFields) {
                try {
                    Field field = bedrockProtocolClass.getDeclaredField(fieldName);
                    modified = modifyStaticIntField(field, minProtocolVersion) || modified;
                    if (debug && modified) {
                        logger.info("成功修改字段 " + fieldName + " 为 " + minProtocolVersion);
                    }
                } catch (NoSuchFieldException ignored) {
                    // 继续尝试下一个字段
                }
            }
            
            // 如果设置了最大协议版本限制
            if (maxProtocolVersion > 0) {
                String[] maxVersionFields = {"MAXIMUM_PROTOCOL_VERSION", "MAX_PROTOCOL_VERSION"};
                for (String fieldName : maxVersionFields) {
                    try {
                        Field field = bedrockProtocolClass.getDeclaredField(fieldName);
                        modified = modifyStaticIntField(field, maxProtocolVersion) || modified;
                        if (debug && modified) {
                            logger.info("成功修改字段 " + fieldName + " 为 " + maxProtocolVersion);
                        }
                    } catch (NoSuchFieldException ignored) {
                        // 继续尝试下一个字段
                    }
                }
            }
            
            if (modified) {
                logger.info("成功修改 BedrockProtocol 类中的版本信息");
            } else {
                logger.warning("未能修改 BedrockProtocol 类中的任何版本字段");
            }
            
            return modified;
        } catch (Exception e) {
            logger.warning("修改 BedrockProtocol 失败: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
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
            
            // 目前我们不实际修改连接处理器，因为这需要字节码操作
            // 返回 false 表示此方法未成功修改任何内容
            return false;
        } catch (Exception e) {
            logger.warning("修改连接处理器失败: " + e.getMessage());
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