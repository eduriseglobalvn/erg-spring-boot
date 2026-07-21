package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.common.response.calendar.CalendarResponses.*;
import com.erg.ergspringboot.model.calendar.CalendarBatch;
import com.erg.ergspringboot.repository.calendar.CalendarBatchRepository;
import com.erg.ergspringboot.repository.calendar.CalendarEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/** Schedule batch listing/detail query boundary. */
@Service
public class CalendarScheduleBatchQueryUseCase {
    private final ObjectProvider<CalendarBatchRepository> batches; private final ObjectProvider<CalendarEventRepository> events;
    private final CalendarBatchRepository compatibilityBatches; private final CalendarEventRepository compatibilityEvents;
    @Autowired public CalendarScheduleBatchQueryUseCase(ObjectProvider<CalendarBatchRepository> batches,ObjectProvider<CalendarEventRepository> events){this.batches=batches;this.events=events;this.compatibilityBatches=null;this.compatibilityEvents=null;}
    public CalendarScheduleBatchQueryUseCase(CalendarBatchRepository batches,CalendarEventRepository events){this.batches=null;this.events=null;this.compatibilityBatches=batches;this.compatibilityEvents=events;}
    public CalendarBatchPageResponse list(String tenantId,String schoolId,String status,Integer page,Integer size){CalendarBatchRepository repository=batchRepository();if(repository==null)return new CalendarBatchPageResponse(List.of(),0,0,0,0,false,false);int p=Math.max(0,page==null?0:page), s=Math.max(1,Math.min(100,size==null?20:size));var result=repository.findTeachingScheduleBatchPage(tenantId,blank(schoolId),normalize(status),PageRequest.of(p,s,Sort.by(Sort.Direction.DESC,"createdAt")));List<CalendarBatchSummaryResponse> items=result.getContent().stream().map(this::summary).toList();return new CalendarBatchPageResponse(items,p,s,(int)result.getTotalElements(),result.getTotalPages(),result.hasNext(),result.hasPrevious());}
    public CalendarBatchDetailResponse detail(String tenantId,String batchId){CalendarBatchRepository repository=batchRepository();CalendarEventRepository eventRepository=eventRepository();if(repository==null||eventRepository==null)throw new IllegalStateException("calendar repositories are not available");CalendarBatch batch=repository.findByTenantIdAndId(tenantId,batchId).orElseThrow(()->new IllegalArgumentException("Batch not found: "+batchId));CalendarBatchSummaryResponse s=summary(batch);return new CalendarBatchDetailResponse(s.id(),s.tenantId(),s.schoolId(),s.academicYear(),s.applyFrom(),s.repeatWeeks(),s.status(),s.source(),s.note(),s.createdBy(),s.publishedBy(),s.publishedAt(),s.createdAt(),(int)eventRepository.countByTenantIdAndBatchId(tenantId,batchId));}
    private CalendarBatchRepository batchRepository(){return compatibilityBatches!=null?compatibilityBatches:batches.getIfAvailable();} private CalendarEventRepository eventRepository(){return compatibilityEvents!=null?compatibilityEvents:events.getIfAvailable();}
    private CalendarBatchSummaryResponse summary(CalendarBatch b){return new CalendarBatchSummaryResponse(b.getId(),b.getTenantId(),b.getSchoolId(),b.getAcademicYear(),b.getApplyFrom()==null?null:b.getApplyFrom().toString(),b.getRepeatWeeks(),b.getStatus(),b.getSource(),b.getNote(),b.getCreatedBy(),b.getPublishedBy(),b.getPublishedAt()==null?null:b.getPublishedAt().toString(),b.getCreatedAt()==null?null:b.getCreatedAt().toString());}
    private String normalize(String v){return v==null||v.isBlank()?null:v.trim().toLowerCase();} private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
}
