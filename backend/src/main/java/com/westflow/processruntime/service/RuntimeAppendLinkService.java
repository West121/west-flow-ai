package com.westflow.processruntime.service;

import com.westflow.processruntime.mapper.RuntimeAppendLinkMapper;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 维护运行时追加与动态构建产生的附属结构记录。
public class RuntimeAppendLinkService {

    private final RuntimeAppendLinkMapper runtimeAppendLinkMapper;

    // 创建一条附属结构关联记录。
    public void createLink(RuntimeAppendLinkRecord record) {
        runtimeAppendLinkMapper.insert(record);
    }

    // 按根流程实例查询全部附属结构。
    public List<RuntimeAppendLinkRecord> listByRootInstanceId(String rootInstanceId) {
        return runtimeAppendLinkMapper.selectByRootInstanceId(rootInstanceId);
    }

    // 按父流程实例查询附属结构。
    public List<RuntimeAppendLinkRecord> listByParentInstanceId(String parentInstanceId) {
        return runtimeAppendLinkMapper.selectByParentInstanceId(parentInstanceId);
    }

    // 按源任务查询附属结构。
    public List<RuntimeAppendLinkRecord> listBySourceTaskId(String sourceTaskId) {
        return runtimeAppendLinkMapper.selectBySourceTaskId(sourceTaskId);
    }

    // 按附属任务实例查询单条记录。
    public RuntimeAppendLinkRecord getByTargetTaskId(String targetTaskId) {
        return runtimeAppendLinkMapper.selectByTargetTaskId(targetTaskId);
    }

    // 按附属子流程实例查询单条记录。
    public RuntimeAppendLinkRecord getByTargetInstanceId(String targetInstanceId) {
        return runtimeAppendLinkMapper.selectByTargetInstanceId(targetInstanceId);
    }

    // 通过附属任务实例更新状态。
    public void updateStatusByTargetTaskId(String targetTaskId, String status, Instant finishedAt) {
        runtimeAppendLinkMapper.updateStatusByTargetTaskId(targetTaskId, status, finishedAt);
    }

    // 通过附属子流程实例更新状态。
    public void updateStatusByTargetInstanceId(String targetInstanceId, String status, Instant finishedAt) {
        runtimeAppendLinkMapper.updateStatusByTargetInstanceId(targetInstanceId, status, finishedAt);
    }
}
