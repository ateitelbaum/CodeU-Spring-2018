package codeu.model.store.basic;

import codeu.model.data.Conversation;
import codeu.model.data.Event;
import codeu.model.data.Message;
import codeu.model.data.User;
import codeu.model.store.persistence.PersistentStorageAgent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;



public class UserStoreTest {

  private UserStore userStore;
  private PersistentStorageAgent mockPersistentStorageAgent;

  private final User USER_ONE = new User(UUID.randomUUID(), "test_username_one",
      "$2a$10$/zf4WlT2Z6tB5sULB9Wec.QQdawmF0f1SbqBw5EeJg5uoVpKFFXAa", Instant.ofEpochMilli(1000));
  private final User USER_TWO = new User(UUID.randomUUID(), "test_username_two",
      "$2a$10$lgZSbmcYyyC7bETcMo/O1uUltWYDK3DW1lrEjCumOE1u8QPMlzNVy", Instant.ofEpochMilli(2000));
  private final User USER_THREE = new User(UUID.randomUUID(), "test_username_three",
      "$2a$10$htXz4E48iPprTexGsEeBFurXyCwW6F6aoiSBqotL4m0NBg/VSkB9.", Instant.ofEpochMilli(3000));

  @Before
  public void setup() {
    mockPersistentStorageAgent = Mockito.mock(PersistentStorageAgent.class);
    userStore = UserStore.getTestInstance(mockPersistentStorageAgent);

    final List<User> userList = new ArrayList<>();
    userList.add(USER_ONE);
    userList.add(USER_TWO);
    userList.add(USER_THREE);
    userStore.setUsers(userList);
  }

  @Test
  public void testGetUser_byUsername_found() {
    User resultUser = userStore.getUser(USER_ONE.getName());

    assertEquals(USER_ONE, resultUser);
  }

  @Test
  public void testGetUser_byId_found() {
    User resultUser = userStore.getUser(USER_ONE.getId());

    assertEquals(USER_ONE, resultUser);
  }

  @Test
  public void testGetUser_byUsername_notFound() {
    User resultUser = userStore.getUser("fake username");

    Assert.assertNull(resultUser);
  }

  @Test
  public void testGetUser_byId_notFound() {
    User resultUser = userStore.getUser(UUID.randomUUID());

    Assert.assertNull(resultUser);
  }

  @Test
  public void testAddUser() {
    User inputUser = new User(UUID.randomUUID(), "test_username",
        "$2a$10$eDhncK/4cNH2KE.Y51AWpeL8/5znNBQLuAFlyJpSYNODR/SJQ/Fg6", Instant.now());

    userStore.addUser(inputUser);
    User resultUser = userStore.getUser("test_username");

    assertEquals(inputUser, resultUser);
    Mockito.verify(mockPersistentStorageAgent).writeThrough(inputUser);
  }

  @Test
  public void testIsUserRegistered_true() {
    Assert.assertTrue(userStore.isUserRegistered(USER_ONE.getName()));
  }

  @Test
  public void testIsUserRegistered_false() {
    Assert.assertFalse(userStore.isUserRegistered("fake username"));
  }

  @Test
  public void testIsUserAdminTrue(){
    Assert.assertTrue(userStore.isUserAdmin("jeremy"));
  }

  @Test
  public void testIsUserAdminFalse(){
    Assert.assertFalse(userStore.isUserAdmin("test_username_two"));
  }

  @Test
  public void testNumUsers(){
    Assert.assertEquals(userStore.getNumUsers(), 3);
  }

  @Test
  public void testNewestUser(){
    Assert.assertEquals(userStore.newestUser(), USER_THREE);
  }

  private void assertEquals(User expectedUser, User actualUser) {
    Assert.assertEquals(expectedUser.getId(), actualUser.getId());
    Assert.assertEquals(expectedUser.getName(), actualUser.getName());
    Assert.assertEquals(expectedUser.getCreationTime(), actualUser.getCreationTime());
  }

  @Test
  public void testFindLatestInstant() {
    Instant instant1 = Instant.now();
    Instant instant2 = Instant.now().plusSeconds(2000);
    HashMap<Instant, Event> fakeEventsMap = new HashMap<Instant, Event>();
    Event fakeEvent1 = new Event(UUID.randomUUID(), "user");
    fakeEventsMap.put(instant1, fakeEvent1);
    Event fakeEvent2 = new Event(UUID.randomUUID(), "user");
    fakeEventsMap.put(instant2, fakeEvent2);

    Instant earlier = userStore.findLatestInstant(fakeEventsMap);

    Assert.assertEquals(earlier, instant2);

  }

}
