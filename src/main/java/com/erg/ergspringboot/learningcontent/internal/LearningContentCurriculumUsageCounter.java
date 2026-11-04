package com.erg.ergspringboot.learningcontent.internal;

import com.erg.ergspringboot.curriculum.CurriculumUsageCounter;
import com.erg.ergspringboot.learningcontent.persistence.TopicContentItemRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Topic content placements under a curriculum node. */
@Component
public class LearningContentCurriculumUsageCounter implements CurriculumUsageCounter {
    private final TopicContentItemRepository contentItems;

    public LearningContentCurriculumUsageCounter(TopicContentItemRepository contentItems) {
        this.contentItems = contentItems;
    }

    @Override
    public Usage count(String tenantId, Node node, String id, List<String> topicIds) {
        long items = node == Node.TOPIC
                ? contentItems.countByTenantIdAndTopicId(tenantId, id)
                : topicIds.isEmpty() ? 0 : contentItems.countByTenantIdAndTopicIdIn(tenantId, topicIds);
        return new Usage(0, 0, 0, items, 0, 0);
    }
}
