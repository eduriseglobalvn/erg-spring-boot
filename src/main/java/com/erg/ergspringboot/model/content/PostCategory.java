package com.erg.ergspringboot.model.content;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "post_categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostCategory extends BaseEntity {

    private String name;

    private String slug;

    private String description;

    private String icon;

    private String metaTitle;

    private String metaDescription;

    private String keywords;

    private Boolean isHidden;

    private String hiddenType;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Post> posts = new LinkedHashSet<>();
}
