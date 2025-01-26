package umm3601.todos;

import org.mongojack.Id;
import org.mongojack.ObjectId;

@SuppressWarnings({"VisibilityModifier"})

public class Todo {
  @ObjectId @Id

  @SuppressWarnings({"MemberName"})

  public String _id;
  public String body;
  public String status;
  public int owner;
  public String category;
  public String $oid;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Todo)) {
      return false;
    }
    Todo other = (Todo) obj;
    return _id.equals(other._id);
  }

  @Override
  public int hashCode() {
    // This means that equal Users will hash the same, which is good.
    return _id.hashCode();
  }
  @Override
  public String toString() {
    return _id;
  }
}
