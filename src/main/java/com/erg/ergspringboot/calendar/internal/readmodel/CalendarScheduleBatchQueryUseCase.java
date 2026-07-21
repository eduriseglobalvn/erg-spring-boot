package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Schedule batch listing/detail query boundary. */
@Service
public class CalendarScheduleBatchQueryUseCase {
    private final CalendarBatchRepository batches; private final CalendarEventRepository events;
    public CalendarScheduleBatchQueryUseCase(CalendarBatchRepository batches, CalendarEventRepository events){this.batches=batches;this.events=events;}
    public CalendarBatchPageResponse list(String tenantId,String schoolId,String status,Integer page,Integer size){int p=Math.max(0,page==null?0:page), s=Math.max(1,Math.min(100,size==null?20:size));var result=batches.findTeachingScheduleBatchPage(tenantId,blank(schoolId),normalize(status),PageRequest.of(p,s,Sort.by(Sort.Direction.DESC,"createdAt")));List<CalendarBatchSummaryResponse> items=result.getContent().stream().map(this::summary).toList();return new CalendarBatchPageResponse(items,p,s,(int)result.getTotalElements(),result.getTotalPages(),result.hasNext(),result.hasPrevious());}
    public CalendarBatchDetailResponse detail(String tenantId,String batchId){CalendarBatch batch=batches.findByTenantIdAndId(tenantId,batchId).orElseThrow(()->new IllegalArgumentException("Batch not found: "+batchId));CalendarBatchSummaryResponse s=summary(batch);return new CalendarBatchDetailResponse(s.id(),s.tenantId(),s.schoolId(),s.academicYear(),s.applyFrom(),s.repeatWeeks(),s.status(),s.source(),s.note(),s.createdBy(),s.publishedBy(),s.publishedAt(),s.createdAt(),(int)events.countByTenantIdAndBatchId(tenantId,batchId));}
    private CalendarBatchSummaryResponse summary(CalendarBatch b){return new CalendarBatchSummaryResponse(b.getId(),b.getTenantId(),b.getSchoolId(),b.getAcademicYear(),b.getApplyFrom()==null?null:b.getApplyFrom().toString(),b.getRepeatWeeks(),b.getStatus(),b.getSource(),b.getNote(),b.getCreatedBy(),b.getPublishedBy(),b.getPublishedAt()==null?null:b.getPublishedAt().toString(),b.getCreatedAt()==null?null:b.getCreatedAt().toString());}
    private String normalize(String v){return v==null||v.isBlank()?null:v.trim().toLowerCase();} private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
}
