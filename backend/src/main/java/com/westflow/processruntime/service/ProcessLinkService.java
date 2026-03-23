package com.westflow.processruntime.service;

import com.westflow.processruntime.mapper.ProcessLinkMapper;
import com.westflow.processruntime.model.ProcessLinkRecord;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 维护主流程与子流程实例关联关系。
public class ProcessLinkService {

    private final ProcessLinkMapper processLinkMapper;

    // 创建一条父子实例关联记录。
    public void createLink(ProcessLinkRecord record) {
        processLinkMapper.insert(record);
    }

    // 查询某个父流程实例下的所有子流程关联。
    public List<ProcessLinkRecord> listByParentInstanceId(String parentInstanceId) {
        return processLinkMapper.selectByParentInstanceId(parentInstanceId);
    }

    // 查询某个根流程实例下的全部父子流程关联。
    public List<ProcessLinkRecord> listByRootInstanceId(String rootInstanceId) {
        return processLinkMapper.selectByRootInstanceId(rootInstanceId);
    }

    // 按子流程实例 id 查询关联记录。
    public ProcessLinkRecord getByChildInstanceId(String childInstanceId) {
        return processLinkMapper.selectByChildInstanceId(childInstanceId);
    }

    // 更新子流程关联状态和结束时间。
    public void updateStatus(String childInstanceId, String status, Instant finishedAt) {
        processLinkMapper.updateStatus(childInstanceId, status, finishedAt);
    }
}
