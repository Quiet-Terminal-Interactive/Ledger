package com.quietterminal.ledger;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.quietterminal.ledger.entity.Activity;
import com.quietterminal.ledger.entity.Role;
import com.quietterminal.ledger.entity.Task;
import com.quietterminal.ledger.entity.User;
import com.quietterminal.ledger.enums.ActivityType;
import com.quietterminal.ledger.enums.TaskStatus;
import com.quietterminal.ledger.error.TaskCreationInvalidException;
import com.quietterminal.ledger.error.TaskUpdateInvalidException;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    private static final Role MEMBER = new Role("Member", Set.of());

    @Test
    void canCreateTaskWithTitleAndDescription() {
        Task task = new Task("Write tests", "Cover the dirty tree");

        assertEquals("Write tests", task.getTitle());
        assertEquals("Cover the dirty tree", task.getDescription());
    }

    @Test
    void canCreateTaskWithAssignees() {
        User user = new User("John", "Smith", MEMBER);
        Task task = new Task("Write tests", "Cover the dirty tree", Set.of(user));

        assertEquals(Set.of(user), task.getAssignees());
    }

    @Test
    void canCreateTaskWithAssigneesAndDueDate() {
        User user = new User("John", "Smith", MEMBER);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        Task task = new Task("Write tests", "Cover the dirty tree", Set.of(user), dueDate);

        assertEquals(Set.of(user), task.getAssignees());
        assertEquals(dueDate, task.getDueDate());
    }

    @Test
    void newTaskHasNotStartedStatus() {
        Task task = new Task("Write tests", "Cover the dirty tree");

        assertEquals(TaskStatus.NOTSTARTED, task.getStatus());
    }

    @Test
    void newTaskHasNoBlockersOrActivity() {
        Task task = new Task("Write tests", "Cover the dirty tree");

        assertTrue(task.getBlockedBy().isEmpty());
        assertTrue(task.getActivityTracker().isEmpty());
        assertFalse(task.isBlocked());
    }

    @Test
    void everyTaskGetsAUniqueNonNullUUID() {
        Task first = new Task("First", "d");
        Task second = new Task("Second", "d");

        assertNotNull(first.getUUID());
        assertNotNull(second.getUUID());
        assertNotEquals(first.getUUID(), second.getUUID());
        assertInstanceOf(UUID.class, first.getUUID());
    }

    @Test
    void cannotUseNullAssigneesOnCreation() {
        assertThrows(NullPointerException.class, () -> new Task("Title", "desc", null));
    }

    @Test
    void cannotUseNullAssigneesOnCreationWithDueDate() {
        assertThrows(NullPointerException.class, () -> new Task("Title", "desc", null, LocalDate.now()));
    }

    @Test
    void cannotUseBlankTitleOnCreation() {
        assertThrows(TaskCreationInvalidException.class, () -> new Task("", "desc"));
    }

    @Test
    void cannotUseNullTitleOnCreation() {
        assertThrows(TaskCreationInvalidException.class, () -> new Task(null, "desc"));
    }

    @Test
    void cannotUseBlankTitleOnCreationWithAssignees() {
        assertThrows(TaskCreationInvalidException.class, () -> new Task("   ", "desc", new HashSet<>()));
    }

    @Test
    void constructorCopiesAssigneesRatherThanAliasing() {
        Set<User> assignees = new HashSet<>(Set.of(new User("John", "Smith", MEMBER)));
        Task task = new Task("Title", "desc", assignees);

        assignees.add(new User("Jane", "Doe", MEMBER));

        assertEquals(1, task.getAssignees().size());
    }

    @Test
    void canUpdateTitleOnHappyPath() {
        Task task = new Task("Title", "desc");

        task.setTitle("New title");

        assertEquals("New title", task.getTitle());
    }

    @Test
    void cannotUpdateTitleToBlank() {
        Task task = new Task("Title", "desc");

        assertThrows(TaskUpdateInvalidException.class, () -> task.setTitle(""));
        assertEquals("Title", task.getTitle());
    }

    @Test
    void cannotUpdateTitleToNull() {
        Task task = new Task("Title", "desc");

        assertThrows(TaskUpdateInvalidException.class, () -> task.setTitle(null));
    }

    @Test
    void canUpdateDescription() {
        Task task = new Task("Title", "desc");

        task.setDescription("New description");

        assertEquals("New description", task.getDescription());
    }

    @Test
    void descriptionCanBeSetToNull() {
        Task task = new Task("Title", "desc");

        task.setDescription(null);

        assertNull(task.getDescription());
    }

    @Test
    void canUpdateDueDate() {
        Task task = new Task("Title", "desc");
        LocalDate dueDate = LocalDate.of(2026, 6, 1);

        task.setDueDate(dueDate);

        assertEquals(dueDate, task.getDueDate());
    }

    @Test
    void canUpdateStatusOnHappyPath() {
        Task task = new Task("Title", "desc");

        task.setStatus(TaskStatus.INPROGRESS);

        assertEquals(TaskStatus.INPROGRESS, task.getStatus());
    }

    @Test
    void cannotSetNullStatus() {
        Task task = new Task("Title", "desc");

        assertThrows(NullPointerException.class, () -> task.setStatus(null));
    }

    @Test
    void cannotCompleteTaskWhileBlockedByIncompleteTask() {
        Task blocker = new Task("Blocker", "desc");
        Task task = new Task("Title", "desc");
        task.addBlocker(blocker);

        assertThrows(TaskUpdateInvalidException.class, () -> task.setStatus(TaskStatus.COMPLETED));
        assertEquals(TaskStatus.NOTSTARTED, task.getStatus());
    }

    @Test
    void canCompleteTaskWhenAllBlockersAreCompleted() {
        Task blocker = new Task("Blocker", "desc");
        blocker.setStatus(TaskStatus.COMPLETED);
        Task task = new Task("Title", "desc");
        task.addBlocker(blocker);

        assertDoesNotThrow(() -> task.setStatus(TaskStatus.COMPLETED));
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
    }

    @Test
    void assignAddsUserToAssignees() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        task.assign(user);

        assertTrue(task.isAssigned(user));
        assertTrue(task.getAssignees().contains(user));
    }

    @Test
    void isAssignedIsFalseForUnassignedUser() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        assertFalse(task.isAssigned(user));
    }

    @Test
    void cannotAssignNullUser() {
        Task task = new Task("Title", "desc");

        assertThrows(NullPointerException.class, () -> task.assign(null));
    }

    @Test
    void unassignRemovesUser() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);
        task.assign(user);

        task.unassign(user);

        assertFalse(task.isAssigned(user));
    }

    @Test
    void unassigningANeverAssignedUserIsANoop() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        assertDoesNotThrow(() -> task.unassign(user));
    }

    @Test
    void getAssigneesIsUnmodifiable() {
        Task task = new Task("Title", "desc");

        assertThrows(UnsupportedOperationException.class,
                () -> task.getAssignees().add(new User("John", "Smith", MEMBER)));
    }

    @Test
    void addBlockerAddsToBlockedBy() {
        Task task = new Task("Title", "desc");
        Task blocker = new Task("Blocker", "desc");

        task.addBlocker(blocker);

        assertTrue(task.isBlockedByTask(blocker));
        assertTrue(task.getBlockedBy().contains(blocker));
    }

    @Test
    void cannotBlockSelf() {
        Task task = new Task("Title", "desc");

        assertThrows(TaskUpdateInvalidException.class, () -> task.addBlocker(task));
    }

    @Test
    void cannotAddNullBlocker() {
        Task task = new Task("Title", "desc");

        assertThrows(NullPointerException.class, () -> task.addBlocker(null));
    }

    @Test
    void cannotCreateCircularBlock() {
        Task first = new Task("First", "desc");
        Task second = new Task("Second", "desc");
        first.addBlocker(second);

        assertThrows(TaskUpdateInvalidException.class, () -> second.addBlocker(first));
    }

    @Test
    void removeBlockerRemovesFromBlockedBy() {
        Task task = new Task("Title", "desc");
        Task blocker = new Task("Blocker", "desc");
        task.addBlocker(blocker);

        task.removeBlocker(blocker);

        assertFalse(task.isBlockedByTask(blocker));
    }

    @Test
    void removingANeverAddedBlockerIsANoop() {
        Task task = new Task("Title", "desc");
        Task blocker = new Task("Blocker", "desc");

        assertDoesNotThrow(() -> task.removeBlocker(blocker));
    }

    @Test
    void getBlockedByIsUnmodifiable() {
        Task task = new Task("Title", "desc");

        assertThrows(UnsupportedOperationException.class, () -> task.getBlockedBy().add(new Task("Other", "desc")));
    }

    @Test
    void isBlockedIsTrueWhenABlockerIsIncomplete() {
        Task task = new Task("Title", "desc");
        Task blocker = new Task("Blocker", "desc");
        task.addBlocker(blocker);

        assertTrue(task.isBlocked());
    }

    @Test
    void isBlockedIsFalseWhenAllBlockersAreCompleted() {
        Task task = new Task("Title", "desc");
        Task blocker = new Task("Blocker", "desc");
        blocker.setStatus(TaskStatus.COMPLETED);
        task.addBlocker(blocker);

        assertFalse(task.isBlocked());
    }

    @Test
    void isBlockedIsFalseWithNoBlockers() {
        Task task = new Task("Title", "desc");

        assertFalse(task.isBlocked());
    }

    @Test
    void recordActivityAddsToTheTracker() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        task.recordActivity(ActivityType.CREATED, user);

        assertEquals(1, task.getActivityTracker().size());
    }

    @Test
    void recordActivityReturnsAnActivityLinkedToTaskAndActor() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        Activity activity = task.recordActivity(ActivityType.CREATED, user);

        assertEquals(task, activity.getTask());
        assertEquals(user, activity.getUser());
        assertEquals(ActivityType.CREATED, activity.getActivity());
    }

    @Test
    void recordActivityPreservesInsertionOrder() {
        Task task = new Task("Title", "desc");
        User user = new User("John", "Smith", MEMBER);

        task.recordActivity(ActivityType.CREATED, user);
        task.recordActivity(ActivityType.STATUS_UPDATED, user);

        List<Activity> activities = task.getActivityTracker();
        assertEquals(ActivityType.CREATED, activities.get(0).getActivity());
        assertEquals(ActivityType.STATUS_UPDATED, activities.get(1).getActivity());
    }

    @Test
    void getActivityTrackerIsUnmodifiable() {
        Task task = new Task("Title", "desc");

        assertThrows(UnsupportedOperationException.class,
                () -> task.getActivityTracker().add(new Activity(task, ActivityType.CREATED, null)));
    }

    @Test
    void taskEqualsItself() {
        Task task = new Task("Title", "desc");

        assertEquals(task, task);
    }

    @Test
    void taskDoesNotEqualADifferentTask() {
        Task first = new Task("First", "desc");
        Task second = new Task("Second", "desc");

        assertNotEquals(first, second);
    }

    @Test
    void taskDoesNotEqualNullOrAnUnrelatedType() {
        Task task = new Task("Title", "desc");

        assertNotEquals(null, task);
        assertNotEquals("Title", task);
    }
}
