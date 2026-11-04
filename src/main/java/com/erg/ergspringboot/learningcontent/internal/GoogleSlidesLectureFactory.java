package com.erg.ergspringboot.learningcontent.internal;

import static com.erg.ergspringboot.learningcontent.internal.LearningContentSupport.normalizedOrDefault;
import static com.erg.ergspringboot.learningcontent.internal.LearningContentSupport.required;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.model.learningcontent.TopicContentItem;
import com.erg.ergspringboot.learningcontent.internal.LearningContentDtos.GoogleSlidesLectureResponse;
import com.erg.ergspringboot.learningcontent.internal.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.learningcontent.internal.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.learningcontent.internal.LearningContentRequests.CreateContentItemRequest;
import com.erg.ergspringboot.learningcontent.internal.LearningContentRequests.CreateGoogleSlidesLectureRequest;
import com.erg.ergspringboot.learningcontent.internal.LearningContentRequests.PlaceContentItemRequest;
import java.net.URI;
import java.util.Map;

/**
 * Creates a Google Slides lecture as one aggregate: LECTURE item, GOOGLE_SLIDE asset, primary asset link
 * and topic placement. Runs inside the facade's single transaction; it calls the collaborators directly,
 * exactly as the facade previously self-invoked its own methods.
 */
final class GoogleSlidesLectureFactory {

    private final ContentItemCommands items;
    private final ContentAssetCommands assets;
    private final TopicPlacementService placements;

    GoogleSlidesLectureFactory(ContentItemCommands items, ContentAssetCommands assets, TopicPlacementService placements) {
        this.items = items;
        this.assets = assets;
        this.placements = placements;
    }

    GoogleSlidesLectureResponse create(String tenantId, String userId, CreateGoogleSlidesLectureRequest request) {
        if (request == null) {
            throw new BadRequestException("lecture request is required");
        }
        String topicId = required(request.topicId(), "topicId");
        String slidesUrl = requiredGoogleSlidesUrl(request.googleSlidesUrl());

        ContentItem item = items.create(tenantId, userId, new CreateContentItemRequest(
                "LECTURE",
                request.title(),
                null,
                normalizedOrDefault(request.status(), "published"),
                normalizedOrDefault(request.visibility(), "public")
        ));
        ContentAsset asset = assets.create(tenantId, userId, new CreateContentAssetRequest(
                "GOOGLE_SLIDE",
                request.title(),
                "google_slides",
                null,
                slidesUrl,
                "application/vnd.google-apps.presentation",
                null,
                null,
                "active",
                Map.of("launchMode", "embed", "canDownload", false)
        ));
        ContentItemAsset assetLink = assets.attach(tenantId, item.getId(), userId, new AttachContentAssetRequest(
                asset.getId(),
                "primary",
                1,
                Map.of("source", "google_slides")
        ));
        TopicContentItem placement = placements.place(tenantId, topicId, userId, new PlaceContentItemRequest(
                item.getId(),
                "LECTURE",
                null,
                "active"
        ));
        return new GoogleSlidesLectureResponse(item, asset, assetLink, placement);
    }

    private String requiredGoogleSlidesUrl(String value) {
        String url = required(value, "googleSlidesUrl");
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("docs.google.com")
                    || !uri.getPath().matches("^/presentation/d/[^/]+/(embed|edit|present|pub)/?$")) {
                throw new BadRequestException("googleSlidesUrl must be a Google Slides presentation URL");
            }
            return url;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("googleSlidesUrl must be a valid URL");
        }
    }
}
