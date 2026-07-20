package com.erg.ergspringboot.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.model.authorization.Permission;
import com.erg.ergspringboot.model.authorization.Role;
import com.erg.ergspringboot.model.authorization.UserPermissionOverride;
import com.erg.ergspringboot.model.auth.AuthSession;
import com.erg.ergspringboot.model.users.EmployeeProfile;
import com.erg.ergspringboot.model.authorization.UserRoleAssignment;
import com.erg.ergspringboot.model.auth.AuthLoginAttempt;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.users.Certificate;
import com.erg.ergspringboot.model.community.CommunityComment;
import com.erg.ergspringboot.model.community.CommunityFollow;
import com.erg.ergspringboot.model.community.CommunityMedia;
import com.erg.ergspringboot.model.community.CommunityPost;
import com.erg.ergspringboot.model.community.CommunityReaction;
import com.erg.ergspringboot.model.content.Post;
import com.erg.ergspringboot.model.recruitment.RecruitmentCandidate;
import com.erg.ergspringboot.model.recruitment.RecruitmentJob;
import com.erg.ergspringboot.model.users.Profile;
import com.erg.ergspringboot.model.users.UserActivity;
import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.model.organization.WorkShift;
import com.erg.ergspringboot.model.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationalModelMappingTest {

    @Test
    void baseEntityIsAJpaMappedSuperclass() throws NoSuchFieldException {
        assertThat(BaseEntity.class).hasAnnotation(MappedSuperclass.class);

        Field id = BaseEntity.class.getDeclaredField("id");
        assertThat(id.isAnnotationPresent(Id.class)).isTrue();
        assertThat(id.getAnnotation(Column.class).name()).isEqualTo("id");
        assertThat(id.getAnnotation(Column.class).length()).isEqualTo(36);

        Field createdAt = BaseEntity.class.getDeclaredField("createdAt");
        assertThat(createdAt.getAnnotation(Column.class).name()).isEqualTo("created_at");

        Field updatedAt = BaseEntity.class.getDeclaredField("updatedAt");
        assertThat(updatedAt.getAnnotation(Column.class).name()).isEqualTo("updated_at");
    }

    @Test
    void baseEntityAssignsUuidV7BeforePersistWhenIdIsMissing() {
        TestEntity entity = new TestEntity();

        entity.onCreate();

        UUID id = UUID.fromString(entity.getId());
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void postgresCoreModelsUseTheirGoTableNames() {
        assertTable(User.class, "users");
        assertTable(AuthSession.class, "user_sessions");
        assertTable(EmployeeProfile.class, "employee_profiles");
        assertTable(UserRoleAssignment.class, "user_role_assignments");
        assertTable(Post.class, "posts");
        assertTable(Profile.class, "profiles");
        assertTable(EducationUnit.class, "education_units");
        assertTable(Role.class, "roles");
        assertTable(Permission.class, "permissions");
    }

    @Test
    void postgresCoreRelationshipsMirrorGormForeignKeys() throws NoSuchFieldException {
        assertManyToOne(AuthSession.class, "userId", "user", "user_id", false);
        assertManyToOne(AuthLoginAttempt.class, "userId", "user", "user_id", true);
        assertManyToOne(UserActivity.class, "userId", "user", "user_id", false);
        assertManyToOne(UserActivity.class, "targetUserId", "targetUser", "target_user_id", true);
        assertManyToOne(UserPermissionOverride.class, "userId", "user", "user_id", false);
        assertManyToOne(UserPermissionOverride.class, "permission", "permissionRef", "permission", false);
        assertManyToOne(Post.class, "categoryId", "category", "category_id", false);
        assertManyToOne(Post.class, "authorId", "author", "author_id", true);
        assertManyToOne(Post.class, "createdById", "createdBy", "created_by_id", true);
        assertManyToOne(Post.class, "publishedById", "publishedBy", "published_by_id", true);
        assertOneToOne(Profile.class, "userId", "user", "user_id", false);
        assertManyToOne(Certificate.class, "userId", "user", "user_id", false);
        assertManyToOne(RecruitmentCandidate.class, "jobId", "job", "job_id", false);
        assertManyToOne(RecruitmentJob.class, "createdBy", "createdByUser", "created_by", true);
        assertManyToOne(WorkShift.class, "userId", "user", "user_id", false);
        assertManyToOne(EducationUnit.class, "parentId", "parent", "parent_id", true);
        assertManyToOne(UserAccessScope.class, "userId", "user", "user_id", false);
        assertManyToOne(UserAccessScope.class, "educationUnitId", "educationUnit", "education_unit_id", false);
        assertManyToOne(CommunityPost.class, "topicId", "topic", "topic_id", false);
        assertManyToOne(CommunityPost.class, "authorId", "author", "author_id", false);
        assertManyToOne(CommunityMedia.class, "postId", "post", "post_id", false);
        assertManyToOne(CommunityComment.class, "postId", "post", "post_id", false);
        assertManyToOne(CommunityComment.class, "authorId", "author", "author_id", false);
        assertManyToOne(CommunityReaction.class, "userId", "user", "user_id", false);
        assertManyToOne(CommunityFollow.class, "userId", "user", "user_id", false);
    }

    @Test
    void usersRolesAndPermissionsUseDirectJpaRelationships() throws NoSuchFieldException {
        assertManyToManyJoinTable(User.class, "roles", "user_roles", "user_id", "role_id");
        assertManyToManyJoinTable(Role.class, "permissions", "role_permissions", "role_id", "permission_id");
        assertOneToOneInverse(User.class, "profile", "user");
        assertOneToManyInverse(User.class, "certificates", "user");
        assertOneToManyInverse(User.class, "workShifts", "user");
        assertOneToManyInverse(User.class, "accessScopes", "user");
        assertOneToManyInverse(User.class, "loginAttempts", "user");
        assertOneToManyInverse(EducationUnit.class, "children", "parent");
        assertOneToManyInverse(RecruitmentJob.class, "candidates", "job");
        assertOneToManyInverse(com.erg.ergspringboot.model.content.PostCategory.class, "posts", "category");
        assertOneToManyInverse(com.erg.ergspringboot.model.community.CommunityTopic.class, "posts", "topic");
        assertOneToManyInverse(CommunityPost.class, "media", "post");
        assertOneToManyInverse(CommunityPost.class, "comments", "post");

        assertThat(classExists("com.erg.ergspringboot.model.core.UserRole")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.core.RolePermission")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.accesscontrol.Role")).isFalse();
        assertThat(classExists("com.erg.ergspringboot.model.accesscontrol.Permission")).isFalse();
    }

    private static void assertTable(Class<?> modelClass, String tableName) {
        assertThat(modelClass).hasAnnotation(Entity.class);
        assertThat(modelClass.getAnnotation(Table.class).name()).isEqualTo(tableName);
    }

    private static void assertManyToOne(
            Class<?> modelClass,
            String foreignKeyField,
            String fieldName,
            String joinColumn,
            boolean nullable
    ) throws NoSuchFieldException {
        Field scalarField = modelClass.getDeclaredField(foreignKeyField);
        assertThat(scalarField.getAnnotation(Column.class).name()).isEqualTo(joinColumn);

        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(ManyToOne.class)).isTrue();
        assertThat(field.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(field.getAnnotation(JoinColumn.class).name()).isEqualTo(joinColumn);
        assertThat(field.getAnnotation(JoinColumn.class).nullable()).isEqualTo(nullable);
    }

    private static void assertOneToOne(
            Class<?> modelClass,
            String foreignKeyField,
            String fieldName,
            String joinColumn,
            boolean nullable
    ) throws NoSuchFieldException {
        Field scalarField = modelClass.getDeclaredField(foreignKeyField);
        assertThat(scalarField.getAnnotation(Column.class).name()).isEqualTo(joinColumn);

        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(OneToOne.class)).isTrue();
        assertThat(field.getAnnotation(OneToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(field.getAnnotation(JoinColumn.class).name()).isEqualTo(joinColumn);
        assertThat(field.getAnnotation(JoinColumn.class).nullable()).isEqualTo(nullable);
    }

    private static void assertOneToOneInverse(
            Class<?> modelClass,
            String fieldName,
            String mappedBy
    ) throws NoSuchFieldException {
        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(OneToOne.class)).isTrue();
        assertThat(field.getAnnotation(OneToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(field.getAnnotation(OneToOne.class).mappedBy()).isEqualTo(mappedBy);
    }

    private static void assertOneToManyInverse(
            Class<?> modelClass,
            String fieldName,
            String mappedBy
    ) throws NoSuchFieldException {
        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(OneToMany.class)).isTrue();
        assertThat(field.getAnnotation(OneToMany.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(field.getAnnotation(OneToMany.class).mappedBy()).isEqualTo(mappedBy);
    }

    private static void assertManyToManyJoinTable(
            Class<?> modelClass,
            String fieldName,
            String tableName,
            String joinColumn,
            String inverseJoinColumn
    ) throws NoSuchFieldException {
        Field field = modelClass.getDeclaredField(fieldName);
        assertThat(field.isAnnotationPresent(ManyToMany.class)).isTrue();
        assertThat(field.getAnnotation(ManyToMany.class).fetch()).isEqualTo(FetchType.LAZY);

        JoinTable joinTable = field.getAnnotation(JoinTable.class);
        assertThat(joinTable.name()).isEqualTo(tableName);
        assertThat(joinTable.joinColumns()).singleElement().satisfies(column ->
                assertThat(column.name()).isEqualTo(joinColumn));
        assertThat(joinTable.inverseJoinColumns()).singleElement().satisfies(column ->
                assertThat(column.name()).isEqualTo(inverseJoinColumn));
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static class TestEntity extends BaseEntity {
    }
}

