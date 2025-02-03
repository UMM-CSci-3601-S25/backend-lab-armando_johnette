package umm3601.todos;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import java.util.ArrayList;
import java.util.List;

import java.util.Objects;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.mongojack.JacksonMongoCollection;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import umm3601.Controller;

/**
 * Controller that manages requests for info about Todos.
 */
public class TodoController implements Controller {

  private static final String API_TODOS = "/api/todos";
  private static final String API_TODO_BY_ID = "/api/todos/{id}";

  public static final String LIMIT_KEY = "limit";
  public static final String STATUS_KEY = "status";
  public static final String BODY_CONTAINS_KEY = "body";
  public static final String OWNER_KEY = "owner";
  public static final String CATEGORY_KEY = "category";
  public static final String SORT_ORDER_KEY = "sortorder";
  //private static final String CATEGORY_REGEX = "^(video games|homework|groceries|software design)$";
  private final JacksonMongoCollection<Todo> todoCollection;

  /**
   * Construct a controller for Todos.
   *
   * @param database the database containing Todo data
   */
  public TodoController(MongoDatabase database) {
    todoCollection = JacksonMongoCollection.builder().build(
        database,
        "todos",
        Todo.class,
        UuidRepresentation.STANDARD);
  }

  /**
   * Set the JSON body of the response to be the single Todo
   * specified by the `id` parameter in the request
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodo(Context ctx) {
    String id = ctx.pathParam("id");
    Todo todo;

    try {
      todo = todoCollection.find(eq("_id", new ObjectId(id))).first();
    } catch (IllegalArgumentException e) {
      throw new BadRequestResponse("The requested Todo id wasn't a legal Mongo Object ID.");
    }
    if (todo == null) {
      throw new NotFoundResponse("The requested Todo was not found");
    } else {
      ctx.json(todo);
      ctx.status(HttpStatus.OK);
    }
  }

  /**
   * Set the JSON body of the response to be a list of all the Todos returned from the database
   * that match any requested filters and ordering
   *
   * @param ctx a Javalin HTTP context
   */
  public void getTodos(Context ctx) {
    Bson combinedFilter = constructFilter(ctx);
    Bson sortingOrder = constructSortingOrder(ctx);

    ArrayList<Todo> matchingTodos = todoCollection
      .find(combinedFilter)
      .sort(sortingOrder)
      .limit(limit(ctx))
      .into(new ArrayList<>());


    ctx.json(matchingTodos);

    ctx.status(HttpStatus.OK);
  }
// filtering the todos by status, body, category, and owner.
// Implementing an api/todos?status=complete (or incomplete) endpoint
// this will let us filter the todos and only return the complete (or incomplete) ones
  
  private Bson constructFilter(Context ctx) {
    List<Bson> filters = new ArrayList<>();
    if (ctx.queryParamMap().containsKey(STATUS_KEY)) {
      String statusParam = ctx.queryParam(STATUS_KEY);
      boolean targetStatus;
      if (statusParam.equalsIgnoreCase("complete") || statusParam.equalsIgnoreCase("true")) {
        targetStatus = true;
      } else if (statusParam.equalsIgnoreCase("incomplete") || statusParam.equalsIgnoreCase("false")) {
        targetStatus = false;
      } else {
        throw new BadRequestResponse("Todo status must be 'complete', 'incomplete', 'true', or 'false'"); // Will throw an error if the status is not complete or incomplete
      }
      filters.add(eq(STATUS_KEY, targetStatus));
    }
    if (ctx.queryParamMap().containsKey(BODY_CONTAINS_KEY)) {
      String targetContent = ctx.queryParam(BODY_CONTAINS_KEY);
      Pattern pattern = Pattern.compile(targetContent, Pattern.CASE_INSENSITIVE);
      filters.add(regex("body", pattern));
    }

    if (ctx.queryParamMap().containsKey(OWNER_KEY)) {
      String targetOwner = ctx.queryParam(OWNER_KEY);
      filters.add(regex("owner", Pattern.compile(targetOwner, Pattern.CASE_INSENSITIVE)));
    }
    if (ctx.queryParamMap().containsKey(CATEGORY_KEY)) {

      Pattern pattern = Pattern.compile(Pattern.quote(ctx.queryParam(CATEGORY_KEY)), Pattern.CASE_INSENSITIVE);
      filters.add(regex(CATEGORY_KEY, pattern));
    }

    Bson combinedFilter = filters.isEmpty() ? new Document() : and(filters);

    return combinedFilter;
  }

 // String category = ctx.queryParamAsClass(CATEGORY_KEY, String.class)
      //   .check(it -> it.matches(CATEGORY_REGEX), "Todo must have a legal Todo category")
      //   .get();
      // filters.add(eq(CATEGORY_KEY, category));


  private Bson constructSortingOrder(Context ctx) {
  // here we are specifying the order in which we want the return todos to be in
    String sortBy = Objects.requireNonNullElse(ctx.queryParam("orderBy"), "owner");
    String sortOrder = Objects.requireNonNullElse(ctx.queryParam("sortorder"), "asc");
    Bson sortingOrder = sortOrder.equals("desc") ? Sorts.descending(sortBy) : Sorts.ascending(sortBy);
    return sortingOrder;
  }
//Implement an api/todos?limit=7 API endpoint, which lets you specify the maximum
//number of todos that the server returns.
//this is the method for limit, the limit is set to 0 (no limit)
  private int limit(Context ctx) {
    int targetLimit = (int) todoCollection.countDocuments();
    if (ctx.queryParamMap().containsKey(LIMIT_KEY)) {
      while (true) {
        targetLimit = ctx.queryParamAsClass(LIMIT_KEY, Integer.class)
          .check(it -> it > 0, "Todo limit must be greater than 0, you gave " + ctx.queryParam(LIMIT_KEY))
          .get();
        if (targetLimit > 0) {
          break;
        }
      }
    }
    return targetLimit;
  }


  /**
   * Get a JSON response with a list of all the Todos.
   *
   * @param ctx a Javalin HTTP context
   */

  public void addRoutes(Javalin server) {
    // Get the specified Todo
    server.get(API_TODO_BY_ID, this::getTodo);

    // List Todos, filtered using query parameters
    server.get(API_TODOS, this::getTodos);

  }
}
