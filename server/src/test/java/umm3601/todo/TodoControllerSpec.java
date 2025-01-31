package umm3601.todo;
import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import io.javalin.validation.BodyValidator;
import io.javalin.validation.Validation;
import io.javalin.validation.ValidationError;
import io.javalin.validation.ValidationException;
import io.javalin.validation.Validator;
import umm3601.todos.Todo;
import umm3601.todos.TodoController;
import umm3601.todos.TodoByCategory;

/**
 * Tests the logic of the UserController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
class TodoControllerSpec {
  private TodoController todoController;

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests. It isn't used all that often, though,
  // which suggests that maybe we should extract the tests that
  // care about it into their own spec file?
  private ObjectId samsId;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  // Used to translate between JSON and POJOs.
  private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /**
   * Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method. It's somewhat expensive to establish a connection to the
   * database, and there are usually limits to how many connections
   * a database will support at once. Limiting ourselves to a single
   * connection that will be shared across all the tests in this spec
   * file helps both speed things up and reduce the load on the DB
   * engine.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito
    // annotations @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "Blanche")
            .append("category", "homework")
            .append("status", "true"));
     testTodos.add(
        new Document()
            .append("owner", "Fry")
            .append("category", "video games")
            .append("status", "false"));
            testTodos.add(
              new Document()
                  .append("owner", "Dawn")
                  .append("category", "homework")
                  .append("status", "true")
                  .append("body","do 3601 homework"));
    samsId = new ObjectId();
    Document sam = new Document()
        .append("_id", samsId)
        .append("owner", "Sam")
        .append("status", true)
        .append("category", "homework");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(sam);

    todoController = new TodoController(db);
  }



  @Test
  void canGetAllTodos() throws IOException {

    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    System.err.println(db.getCollection("todos").countDocuments());
    System.err.println(todoArrayListCaptor.getValue().size());

    assertEquals(
        db.getCollection("todos").countDocuments(),
        todoArrayListCaptor.getValue().size());
  }

  /**
   * Confirm that if we process a request for users with age 37,
   * that all returned users have that age, and we get the correct
   * number of users.
   *
   * The structure of this test is:
   *
   *    - We create a `Map` for the request's `queryParams`, that
   *      contains a single entry, mapping the `AGE_KEY` to the
   *      target value ("37"). This "tells" our `UserController`
   *      that we want all the `User`s that have age 37.
   *    - We create a validator that confirms that the code
   *      we're testing calls `ctx.queryParamsAsClass("age", Integer.class)`,
   *      i.e., it asks for the value in the query param map
   *      associated with the key `"age"`, interpreted as an Integer.
   *      That call needs to return a value of type `Validator<Integer>`
   *      that will succeed and return the (integer) value `37` associated
   *      with the (`String`) parameter value `"37"`.
   *    - We then call `userController.getUsers(ctx)` to run the code
   *      being tested with the constructed context `ctx`.
   *    - We also use the `userListArrayCaptor` (defined above)
   *      to capture the `ArrayList<User>` that the code under test
   *      passes to `ctx.json(…)`. We can then confirm that the
   *      correct list of users (i.e., all the users with age 37)
   *      is passed in to be returned in the context.
   *    - Now we can use a variety of assertions to confirm that
   *      the code under test did the "right" thing:
   *       - Confirm that the list of users has length 2
   *       - Confirm that each user in the list has age 37
   *       - Confirm that their names are "Jamie" and "Pat"
   *
   * @throws IOException
   */
  // @Test
  // void canGetUsersWithAge37() throws IOException {
  //   // We'll need both `String` and `Integer` representations of
  //   // the target age, so I'm defining both here.
  //   Integer targetAge = 37;
  //   String targetAgeString = targetAge.toString();

  //   // Create a `Map` for the `queryParams` that will "return" the string
  //   // "37" if you ask for the value associated with the `AGE_KEY`.
  //   Map<String, List<String>> queryParams = new HashMap<>();

  //   //queryParams.put(UserController.AGE_KEY, Arrays.asList(new String[] {targetAgeString}));
  //   // When the code being tested calls `ctx.queryParamMap()` return the
  //   // the `queryParams` map we just built.
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   // When the code being tested calls `ctx.queryParam(AGE_KEY)` return the
  //   // `targetAgeString`.
  //   //when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(targetAgeString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the integer value 37.
  //   Validation validation = new Validation();
  //   // The `AGE_KEY` should be name of the key whose value is being validated.
  //   // You can actually put whatever you want here, because it's only used in the generation
  //   // of testing error reports, but using the actually key value will make those reports more informative.
  //   //Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, targetAgeString);
  //   // When the code being tested calls `ctx.queryParamAsClass("age", Integer.class)`
  //   // we'll return the `Validator` we just constructed.
  //   //when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class))
  //   //    .thenReturn(validator);

  //   todoController.getTodo(ctx);

  //   // Confirm that the code being tested calls `ctx.json(…)`, and capture whatever
  //   // is passed in as the argument when `ctx.json()` is called.
  //   verify(ctx).json(userArrayListCaptor.capture());
  //   // Confirm that the code under test calls `ctx.status(HttpStatus.OK)` is called.
  //   verify(ctx).status(HttpStatus.OK);

  //   // Confirm that we get back two users.
  //   assertEquals(2, userArrayListCaptor.getValue().size());
  //   // Confirm that both users have age 37.
  //   // for (User user : userArrayListCaptor.getValue()) {
  //   //   assertEquals(targetAge, user.age);
  //   // }
  //   // Generate a list of the names of the returned users.
  //   List<String> names = userArrayListCaptor.getValue().stream().map(user -> user.name).collect(Collectors.toList());
  //   // Confirm that the returned `names` contain the two names of the
  //   // 37-year-olds.
  //   assertTrue(names.contains("Jamie"));
  //   assertTrue(names.contains("Pat"));
  // }

  /**
   * Confirm that if we process a request for users with age 37,
   * that all returned users have that age, and we get the correct
   * number of users.
   *
   * Instead of using the Captor like in many other tests, in this test
   * we use an ArgumentMatcher just to show how that can be used, illustrating
   * another way to test the same thing.
   *
   * An `ArgumentMatcher` has a method `matches` that returns `true`
   * if the argument passed to `ctx.json(…)` (a `List<User>` in this case)
   * has the desired properties.
   *
   * This is probably overkill here, but it does illustrate a different
   * approach to writing tests.
   *
   * @throws JsonMappingException
   * @throws JsonProcessingException
   */
  // @Test
  // void canGetUsersWithAge37Redux() throws JsonMappingException, JsonProcessingException {
  //   // We'll need both `String` and `Integer` representations of
  //   // the target age, so I'm defining both here.
  //   Integer targetAge = 37;
  //   String targetAgeString = targetAge.toString();

  //   // When the controller calls `ctx.queryParamMap`, return the expected map for an
  //   // "?age=37" query.
  //   when(ctx.queryParamMap()).thenReturn(Map.of(UserController.AGE_KEY, List.of(targetAgeString)));
  //   // When the code being tested calls `ctx.queryParam(AGE_KEY)` return the
  //   // `targetAgeString`.
  //   when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(targetAgeString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the integer value 37.
  //   Validation validation = new Validation();
  //   // The `AGE_KEY` should be name of the key whose value is being validated.
  //   // You can actually put whatever you want here, because it's only used in the generation
  //   // of testing error reports, but using the actually key value will make those reports more informative.
  //   Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, targetAgeString);
  //   when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class)).thenReturn(validator);

  //   // Call the method under test.
  //   userController.getUsers(ctx);

  //   // Verify that `getUsers` included a call to `ctx.status(HttpStatus.OK)` at some
  //   // point.
  //   verify(ctx).status(HttpStatus.OK);

  //   // Verify that `ctx.json()` is called with a `List` of `User`s.
  //   // Each of those `User`s should have age 37.
  //   verify(ctx).json(argThat(new ArgumentMatcher<List<User>>() {
  //     @Override
  //     public boolean matches(List<User> users) {
  //       for (User user : users) {
  //         assertEquals(targetAge, user.age);
  //       }
  //       assertEquals(2, users.size());
  //       return true;
  //     }
  //   }));
  // }

  /**
   * Test that if the user sends a request with an illegal value in
   * the age field (i.e., something that can't be parsed to a number)
   * we get a reasonable error back.
   */
  // @Test
  // void respondsAppropriatelyToNonNumericAge() {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   String illegalIntegerString = "bad integer string";
  //   queryParams.put(UserController.AGE_KEY, Arrays.asList(new String[] {illegalIntegerString}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   // When the code being tested calls `ctx.queryParam(AGE_KEY)` return the
  //   // `illegalIntegerString`.
  //   when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(illegalIntegerString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the `illegalIntegerString`.
  //   Validation validation = new Validation();
  //   // The `AGE_KEY` should be name of the key whose value is being validated.
  //   // You can actually put whatever you want here, because it's only used in the generation
  //   // of testing error reports, but using the actually key value will make those reports more informative.
  //   Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, illegalIntegerString);
  //   when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class)).thenReturn(validator);

  //   // This should now throw a `ValidationException` because
  //   // our request has an age that can't be parsed to a number.
  //   ValidationException exception = assertThrows(ValidationException.class, () -> {
  //     userController.getUsers(ctx);
  //   });
  //   // This digs into the returned `ValidationException` to get the underlying `Exception` that caused
  //   // the validation to fail:
  //   //   - `exception.getErrors` returns a `Map` that maps keys (like `AGE_KEY`) to lists of
  //   //      validation errors for that key
  //   //   - `.get(AGE_KEY)` returns a list of all the validation errors associated with `AGE_KEY`
  //   //   - `.get(0)` assumes that the root cause is the first error in the list. In our case there
  //   //     is only one root cause,
  //   //     so that's safe, but you might be careful about that assumption in other contexts.
  //   //   - `.exception()` gets the actually `Exception` value that was the underlying cause
  //   Exception exceptionCause = exception.getErrors().get(UserController.AGE_KEY).get(0).exception();
  //   // The cause should have been a `NumberFormatException` (what is thrown when we try to parse "bad" as an integer).
  //   assertEquals(NumberFormatException.class, exceptionCause.getClass());
  //   // The message for that `NumberFOrmatException` should include the text it tried to parse as an integer,
  //   // i.e., `"bad integer string"`.
  //   assertTrue(exceptionCause.getMessage().contains(illegalIntegerString));
  // }

  /**
   * Test that if the user sends a request with an illegal value in
   * the age field (i.e., too big of a number)
   * we get a reasonable error code back.
   */
  // @Test
  // void respondsAppropriatelyToTooLargeNumberAge() {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   String overlyLargeAgeString = "151";
  //   queryParams.put(UserController.AGE_KEY, Arrays.asList(new String[] {overlyLargeAgeString}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   // When the code being tested calls `ctx.queryParam(AGE_KEY)` return the
  //   // `overlyLargeAgeString`.
  //   when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(overlyLargeAgeString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the integer value 37.
  //   Validation validation = new Validation();
  //   // The `AGE_KEY` should be name of the key whose value is being validated.
  //   // You can actually put whatever you want here, because it's only used in the generation
  //   // of testing error reports, but using the actually key value will make those reports more informative.
  //   Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, overlyLargeAgeString);
  //   when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class)).thenReturn(validator);

  //   // This should now throw a `ValidationException` because
  //   // our request has an age that is larger than 150, which isn't allowed.
  //   ValidationException exception = assertThrows(ValidationException.class, () -> {
  //     userController.getUsers(ctx);
  //   });
  //   // This `ValidationException` was caused by a custom check, so we just get the message from the first
  //   // error and confirm that it contains the problematic string, since that would be useful information
  //   // for someone trying to debug a case where this validation fails.
  //   String exceptionMessage = exception.getErrors().get(UserController.AGE_KEY).get(0).getMessage();
  //   // The message should be the message from our code under test, which should include the text we
  //   // tried to parse as an age, namely "151".
  //   assertTrue(exceptionMessage.contains(overlyLargeAgeString));
  // }

  /**
   * Test that if the user sends a request with an illegal value in
   * the age field (i.e., too small of a number)
   * we get a reasonable error code back.
   */
  // @Test
  // void respondsAppropriatelyToTooSmallNumberAge() {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   String negativeAgeString = "-1";
  //   queryParams.put(UserController.AGE_KEY, Arrays.asList(new String[] {negativeAgeString}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   // When the code being tested calls `ctx.queryParam(AGE_KEY)` return the
  //   // `negativeAgeString`.
  //   when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(negativeAgeString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the string value `negativeAgeString`.
  //   Validation validation = new Validation();
  //   // The `AGE_KEY` should be name of the key whose value is being validated.
  //   // You can actually put whatever you want here, because it's only used in the generation
  //   // of testing error reports, but using the actually key value will make those reports more informative.
  //   Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, negativeAgeString);
  //   when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class)).thenReturn(validator);

  //   // This should now throw a `ValidationException` because
  //   // our request has an age that is larger than 150, which isn't allowed.
  //   ValidationException exception = assertThrows(ValidationException.class, () -> {
  //     userController.getUsers(ctx);
  //   });
  //   // This `ValidationException` was caused by a custom check, so we just get the message from the first
  //   // error and confirm that it contains the problematic string, since that would be useful information
  //   // for someone trying to debug a case where this validation fails.
  //   String exceptionMessage = exception.getErrors().get(UserController.AGE_KEY).get(0).getMessage();
  //   // The message should be the message from our code under test, which should include the text we
  //   // tried to parse as an age, namely "-1".
  //   assertTrue(exceptionMessage.contains(negativeAgeString));
  // }

  @Test
  void canGetTodosWithCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"true"}));
    queryParams.put(TodoController.SORT_ORDER_KEY, Arrays.asList(new String[] {"desc"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("true");
    when(ctx.queryParam(TodoController.SORT_ORDER_KEY)).thenReturn("desc");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("true", todo.category);
    }
  }

  @Test
  void canGetTodosWithOwner() throws IOException {
    String targetOwner = "Fry";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {targetOwner}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn("Fry");

  Validation validation = new Validation();
  Validator<String> validator = validation.validator(TodoController.OWNER_KEY,String.class, targetOwner);

  when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator);

   todoController.getTodos(ctx);


    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner,todo.owner);
    }
  }

  @Test
  void canGetTodosWithBody() throws IOException {
    String targetOwner = "do 3601 homework";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.BODY_CONTAINS_KEY, Arrays.asList(new String[] {targetOwner}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.BODY_CONTAINS_KEY)).thenReturn("do 3601 homework");

  Validation validation = new Validation();
  Validator<String> validator = validation.validator(TodoController.OWNER_KEY,String.class, targetOwner);

  when(ctx.queryParamAsClass(TodoController.BODY_CONTAINS_KEY, String.class)).thenReturn(validator);

   todoController.getTodos(ctx);


    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner,todo.body);
    }
  }

  @Test
  void canGetTodosWithStatus() throws IOException {
    Boolean targetOwner = true;
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(String.valueOf(targetOwner)));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(String.valueOf(targetOwner));

  Validation validation = new Validation();
  Validator<String> validator = validation.validator(TodoController.STATUS_KEY,String.class, String.valueOf(targetOwner));

  when(ctx.queryParamAsClass(TodoController.STATUS_KEY, String.class)).thenReturn(validator);

   todoController.getTodos(ctx);


    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner,todo.status);
    }
  }

  @Test
  void canGetTodosWithCategoryLowercase() throws IOException {
    String targetCategory = "homework";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {targetCategory}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("homework");

  Validation validation = new Validation();
  Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY,String.class, targetCategory);

  when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);

   todoController.getTodos(ctx);


    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetCategory,todo.category);
    }
  }

  // @Test
  // void getTodosByRole() throws IOException {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   String roleString = "viewer";
  //   queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {roleString}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `ROLE_KEY` we get back a string that represents a legal role.
  //   Validation validation = new Validation();
  //   Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, roleString);
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator);

  //   todoController.getTodos(ctx);

  //   verify(ctx).json(todoArrayListCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals(2, todoArrayListCaptor.getValue().size());
  // }

  // @Test
  // void getUsersByCompanyAndAge() throws IOException {
  //   String targetCompanyString = "OHMNET";
  //   Integer targetAge = 37;
  //   String targetAgeString = targetAge.toString();

  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(UserController.COMPANY_KEY, Arrays.asList(new String[] {targetCompanyString}));
  //   queryParams.put(UserController.AGE_KEY, Arrays.asList(new String[] {targetAgeString}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParam(UserController.COMPANY_KEY)).thenReturn(targetCompanyString);

  //   // Create a validator that confirms that when we ask for the value associated with
  //   // `AGE_KEY` _as an integer_, we get back the integer value 37.
  //   Validation validation = new Validation();
  //   Validator<Integer> validator = validation.validator(UserController.AGE_KEY, Integer.class, targetAgeString);
  //   when(ctx.queryParamAsClass(UserController.AGE_KEY, Integer.class)).thenReturn(validator);
  //   when(ctx.queryParam(UserController.AGE_KEY)).thenReturn(targetAgeString);

  //   userController.getUsers(ctx);

  //   verify(ctx).json(userArrayListCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals(1, userArrayListCaptor.getValue().size());
  //   for (User user : userArrayListCaptor.getValue()) {
  //     assertEquals(targetCompanyString, user.company);
  //     assertEquals(targetAge, user.age);
  //   }
  // }

  // @Test
  // void getUserWithExistentId() throws IOException {
  //   String id = samsId.toHexString();
  //   when(ctx.pathParam("id")).thenReturn(id);

  //   userController.getUser(ctx);

  //   verify(ctx).json(userCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals("Sam", userCaptor.getValue().name);
  //   assertEquals(samsId.toHexString(), userCaptor.getValue()._id);
  // }

  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested Todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested Todo was not found", exception.getMessage());

  }


}


