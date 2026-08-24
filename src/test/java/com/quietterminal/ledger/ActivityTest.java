package com.quietterminal.ledger;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.entity.Activity;
import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.ActivityType;

import static org.junit.jupiter.api.Assertions.*;

class ActivityTest {

    private static final Role MEMBER = new Role("Member", Set.of());

    @Test
    void canCreateActivityWithTaskTypeAndUser() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        Activity activity = new Activity(task, ActivityType.CREATED, user);

        assertEquals(task, activity.getTask());
        assertEquals(ActivityType.CREATED, activity.getActivity());
        assertEquals(user, activity.getUser());
    }

    @Test
    void canCreateActivityWithoutAUser() {
        Task task = new Task("Title", "desc");

        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertNull(activity.getUser());
    }

    @Test
    void everyActivityGetsAUniqueNonNullUUID() {
        Task task = new Task("Title", "desc");

        Activity first = new Activity(task, ActivityType.CREATED, null);
        Activity second = new Activity(task, ActivityType.CREATED, null);

        assertNotNull(first.getUUID());
        assertNotNull(second.getUUID());
        assertNotEquals(first.getUUID(), second.getUUID());
        assertInstanceOf(UUID.class, first.getUUID());
    }

    @Test
    void activityTimestampIsSetToNowOnCreation() {
        Task task = new Task("Title", "desc");
        long before = System.currentTimeMillis() / 1000;

        Activity activity = new Activity(task, ActivityType.CREATED, null);

        long after = System.currentTimeMillis() / 1000;
        assertNotNull(activity.getTimestamp());
        assertTrue(activity.getTimestamp() >= before && activity.getTimestamp() <= after);
    }

    @Test
    void cannotCreateActivityWithNullTask() {
        assertThrows(NullPointerException.class, () -> new Activity(null, ActivityType.CREATED, null));
    }

    @Test
    void cannotCreateActivityWithNullActivityType() {
        Task task = new Task("Title", "desc");

        assertThrows(NullPointerException.class, () -> new Activity(task, null, null));
    }

    @Test
    void newActivityHasEmptyMetadata() {
        Task task = new Task("Title", "desc");

        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertTrue(activity.getMetadata().isEmpty());
    }

    @Test
    void canPutMetadata() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        activity.putMetadata("key", "value");

        assertEquals("value", activity.getMetadata().get("key"));
    }

    @Test
    void puttingMetadataWithAnExistingKeyOverwritesTheValue() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);
        activity.putMetadata("key", "first");

        activity.putMetadata("key", "second");

        assertEquals("second", activity.getMetadata().get("key"));
    }

    @Test
    void metadataValueCanBeNull() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertDoesNotThrow(() -> activity.putMetadata("key", null));
        assertTrue(activity.getMetadata().containsKey("key"));
    }

    @Test
    void cannotPutMetadataWithANullKey() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertThrows(NullPointerException.class, () -> activity.putMetadata(null, "value"));
    }

    @Test
    void getMetadataIsUnmodifiable() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertThrows(UnsupportedOperationException.class, () -> activity.getMetadata().put("key", "value"));
    }

    @Test
    void activityEqualsItself() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertEquals(activity, activity);
    }

    @Test
    void activityDoesNotEqualADifferentActivity() {
        Task task = new Task("Title", "desc");
        Activity first = new Activity(task, ActivityType.CREATED, null);
        Activity second = new Activity(task, ActivityType.CREATED, null);

        assertNotEquals(first, second);
    }

    @Test
    void activityDoesNotEqualNullOrAnUnrelatedType() {
        Task task = new Task("Title", "desc");
        Activity activity = new Activity(task, ActivityType.CREATED, null);

        assertNotEquals(null, activity);
        assertNotEquals("Title", activity);
    }
}
