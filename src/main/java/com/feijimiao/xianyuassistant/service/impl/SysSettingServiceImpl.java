package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuSysSetting;
import com.feijimiao.xianyuassistant.mapper.XianyuSysSettingMapper;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import com.feijimiao.xianyuassistant.service.bo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统配置服务实现
 * @author IAMLZY
 * @date 2026/4/22
 */
@Slf4j
@Service
public class SysSettingServiceImpl implements SysSettingService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private XianyuSysSettingMapper sysSettingMapper;

    @Override
    public String getSettingValue(String settingKey) {
        if (settingKey == null || settingKey.trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<XianyuSysSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuSysSetting::getSettingKey, settingKey.trim());
        XianyuSysSetting setting = sysSettingMapper.selectOne(wrapper);

        return setting != null ? setting.getSettingValue() : null;
    }

    @Override
    public GetSettingRespBO getSetting(GetSettingReqBO reqBO) {
        if (reqBO == null || reqBO.getSettingKey() == null || reqBO.getSettingKey().trim().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<XianyuSysSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuSysSetting::getSettingKey, reqBO.getSettingKey().trim());
        XianyuSysSetting setting = sysSettingMapper.selectOne(wrapper);

        if (setting == null) {
            return null;
        }

        GetSettingRespBO respBO = new GetSettingRespBO();
        respBO.setSettingKey(setting.getSettingKey());
        respBO.setSettingValue(setting.getSettingValue());
        respBO.setSettingDesc(setting.getSettingDesc());
        return respBO;
    }

    @Override
    public List<GetSettingRespBO> getAllSettings() {
        List<XianyuSysSetting> settings = sysSettingMapper.selectList(null);
        List<GetSettingRespBO> result = new ArrayList<>();

        for (XianyuSysSetting setting : settings) {
            GetSettingRespBO respBO = new GetSettingRespBO();
            respBO.setSettingKey(setting.getSettingKey());
            respBO.setSettingValue(setting.getSettingValue());
            respBO.setSettingDesc(setting.getSettingDesc());
            result.add(respBO);
        }

        return result;
    }

    @Override
    public void saveSetting(SaveSettingReqBO reqBO) {
        if (reqBO == null || reqBO.getSettingKey() == null || reqBO.getSettingKey().trim().isEmpty()) {
            throw new RuntimeException("配置键不能为空");
        }

        String now = LocalDateTime.now().format(FORMATTER);

        // 查询是否已存在
        LambdaQueryWrapper<XianyuSysSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuSysSetting::getSettingKey, reqBO.getSettingKey().trim());
        XianyuSysSetting existing = sysSettingMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新
            existing.setSettingValue(reqBO.getSettingValue());
            existing.setSettingDesc(reqBO.getSettingDesc());
            existing.setUpdatedTime(now);
            sysSettingMapper.updateById(existing);
            log.info("[SysSetting] 更新配置成功: key={}", reqBO.getSettingKey());
        } else {
            // 新增
            XianyuSysSetting setting = new XianyuSysSetting();
            setting.setSettingKey(reqBO.getSettingKey().trim());
            setting.setSettingValue(reqBO.getSettingValue());
            setting.setSettingDesc(reqBO.getSettingDesc());
            setting.setCreatedTime(now);
            setting.setUpdatedTime(now);
            sysSettingMapper.insert(setting);
            log.info("[SysSetting] 新增配置成功: key={}", reqBO.getSettingKey());
        }
    }

    @Override
    public void deleteSetting(String settingKey) {
        if (settingKey == null || settingKey.trim().isEmpty()) {
            throw new RuntimeException("配置键不能为空");
        }

        LambdaQueryWrapper<XianyuSysSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(XianyuSysSetting::getSettingKey, settingKey.trim());
        sysSettingMapper.delete(wrapper);
        log.info("[SysSetting] 删除配置成功: key={}", settingKey);
    }
}
