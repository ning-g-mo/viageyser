package cn.ningmo.viageyser;

import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.GeyserImpl;

public class ViaGeyser extends JavaPlugin {

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        
        // 检查是否启用插件
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("ViaGeyser 已在配置中禁用");
            return;
        }
        
        getLogger().info("ViaGeyser 插件正在启动...");
        
        // 等待 Geyser 完全加载
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                // 确保 Geyser 已加载
                if (GeyserImpl.getInstance() == null) {
                    getLogger().warning("Geyser 尚未加载，无法应用版本钩子");
                    return;
                }
                
                // 从配置中获取版本设置
                int minVersion = getConfig().getInt("min-protocol-version", 400);
                int maxVersion = getConfig().getInt("max-protocol-version", -1);
                boolean debug = getConfig().getBoolean("debug", false);
                
                if (debug) {
                    getLogger().info("调试模式已启用");
                    getLogger().info("最低协议版本: " + minVersion);
                    getLogger().info("最高协议版本: " + (maxVersion == -1 ? "不限制" : maxVersion));
                }
                
                // 应用协议版本钩子
                ProtocolVersionHook hook = new ProtocolVersionHook(getLogger());
                hook.setMinProtocolVersion(minVersion);
                hook.setMaxProtocolVersion(maxVersion);
                hook.setDebug(debug);
                
                boolean success = hook.applyHook();
                
                if (success) {
                    getLogger().info("成功应用 Geyser 协议版本钩子！");
                } else {
                    getLogger().warning("应用 Geyser 协议版本钩子失败，请检查日志获取详细信息");
                }
            } catch (Exception e) {
                getLogger().severe("启用 ViaGeyser 时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }, 40L); // 等待 2 秒确保 Geyser 已完全加载
    }

    @Override
    public void onDisable() {
        getLogger().info("ViaGeyser 插件已禁用");
    }
} 