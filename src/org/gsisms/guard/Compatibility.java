package org.gsisms.guard;

/** Advisory compatibility policy, independent of device name and ROM identity. */
final class Compatibility {
    final boolean hook, builtin, mounted, masterNamespace, database, powerOptEnabled, bufferEvidence, services;
    Compatibility(boolean hook, boolean builtin, boolean mounted, boolean masterNamespace,
                  boolean database, boolean powerOptEnabled, boolean bufferEvidence, boolean services) {
        this.hook=hook;this.builtin=builtin;this.mounted=mounted;this.masterNamespace=masterNamespace;
        this.database=database;this.powerOptEnabled=powerOptEnabled;this.bufferEvidence=bufferEvidence;this.services=services;
    }
    boolean readyRecommended(){return hook&&!builtin&&!mounted&&(powerOptEnabled||bufferEvidence);}
    boolean mountRecommended(){return database&&masterNamespace&&services&&!builtin&&!mounted&&powerOptEnabled;}
    String readyWarnings(){
        StringBuilder s=new StringBuilder();
        if(!hook)s.append("未检测到可访问的高通 OEM hook；尝试可能失败。\n");
        if(builtin)s.append("系统已有 qcom-atel-ready，可能重复上报。\n");
        if(mounted)s.append("radio 目录已有挂载，无法单独判断 ATEL 的效果。\n");
        if(!powerOptEnabled&&!bufferEvidence)s.append("未确认 Power Opt 开启或短信缓冲证据，可能与本故障无关。\n");
        return s.toString().trim();
    }
    String mountWarnings(){
        StringBuilder s=new StringBuilder();
        if(!database)s.append("未确认兼容数据库结构；不能安全修改副本。\n");
        if(!masterNamespace)s.append("root 不在主挂载空间；挂载不能可靠作用于无线服务。\n");
        if(!services)s.append("未识别可控制的 RIL 服务；不能安全完成目录复制。\n");
        if(builtin)s.append("系统已有 ATEL 修复，挂载可能没有必要。\n");
        if(mounted)s.append("radio 已有挂载；不会覆盖其他工具的挂载。\n");
        if(!powerOptEnabled)s.append("Power Opt 未确认开启，关闭它可能没有效果。\n");
        return s.toString().trim();
    }
    boolean allowReady(boolean force){return force||readyRecommended();}
    boolean allowMount(boolean force){return force||mountRecommended();}
}
