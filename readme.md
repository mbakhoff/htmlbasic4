# Advanced web application with Spring

## Database & repositories

In the previous tutorials we saved our forums data to regular files.
This might be a good idea when we have really simple data structures, but won't scale that well when more classes come into play.
It's a good time to migrate our valuable forum data to a real database before we start storing more information in our forums.

We're going to use two popular libraries for storing data in the database: Hibernate ORM and Spring Data JPA.
Both of them have a non trivial learning curve, but both are very popular among Java developers and can help you get things done a lot faster once you master them.
Take your time and try to really understand how they work.

When you have trouble, then check the docs for [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/) and [Hibernate ORM](https://docs.jboss.org/hibernate/orm/5.2/userguide/html_single/Hibernate_User_Guide.html).
This repository also contains a working sample - see classes `SampleController`, `SampleItem` and `SampleItemRepository`.

### Setting up the database

All the following is already configured, but it's good to point out a few things:
* the pom.xml adds new dependencies for our database (H2 database) and the spring data plugin.
* the application.properties file contains configuration for spring about connecting to the database:
  * `spring.datasource.url` specifies where the database will store its files,
  * `spring.jpa.properties.hibernate.hbm2ddl.auto` specifies that spring should automatically create and update our database tables,
  * `spring.jpa.properties.hibernate.dialect` tells Hibernate that we're using the H2 database

H2 is an embedded database.
You don't have to install or start it manually.
Maven will download it for you.
Spring will start it automatically in the background when the server is starting.
Embedded means that it's not even a separate process - it will run in the same process as our server.

### Storing data in the database

Databases are really good at storing data.
You can find specific items super fast even if the database contains massive amounts of data.
Also, they can handle multiple users reading and modifying data concurrently, which is tricky to get right with plain files.

The data in a database is stored in tables.
Each table has rows and columns.
Usually most tables have the `id` column, that is used to identify the row and link rows from different tables.
When mapping java objects to database tables, each class has a separate table in the database and each field of the class is stored in a separate column.
When the class contains collections (lists or sets), then a separate table is created for each collection.
These contains pairs of `<object id, element of the collection>`.

Hibernate and Spring Data make it easier to use the database.
Given a java class, Hibernate can automatically generate the database tables for holding objects of the given class.
Spring Data makes it easy to find specific items from the database by their id or field value (Spring Data uses Hibernate internally).

To use Hibernate, you should annotate all classes that will be stored in the database with the `@Entity` annotation.
When the server starts up, Hibernate will scan all classes, find the annotated classes and figure out how to store them in the database.

You should also add an `id` field of type `Long` to each of your entity classes.
This enables Hibernate to match the java objects to the database table rows.

Finally, you should annotate your collection fields with association annotations (`@OneToOne`, `@OneToMany`, `@ManyToMany`) so that hibernate will know how to connect the database tables.
Internally that works by using foreign keys and junction tables, but it's all automated by Hibernate.

Here is an example of an entity class:
```java
@Entity
public class Person {

    @Id
    @GeneratedValue
    private Long id;

    @OneToMany
    private List<Phone> phones = new ArrayList<>();

    // getters, setters
}

@Entity
public class Phone {

    @Id
    @GeneratedValue
    private Long id;

    private String number;

    // getters, setters
}
```

To recap:
* `@Entity` tells Hibernate that objects of this class will be stored in a database
* `@Id` tells Hibernate to store the table row id in the annotated field
* `@GeneratedValue` tells Hibernate to assign the object a new unique id when saving it (unless it already has an id).
* `@OneToMany` tells Hibernate that one person can have many phones

### Using repositories

How does one actually save and fetch the objects from the database?
Spring Data introduces the concept of a Repository - an interface that defines methods to save and find objects.
You should create a repository for each of your entity classes (if needed) by extending the CrudRepository interface (from Spring Data):

```java
public interface CrudRepository<T, ID> extends Repository<T, ID> {
  T save(T entity);
  T findOne(ID primaryKey);
  Iterable<T> findAll();
  void delete(T entity);
  // more methods
}
```

For example, to create a repository for Person, you would write:

```java
public interface PersonRepository extends CrudRepository<Person, Long> {
  // bunch of useful methods inherited from CrudRepository
}
```

The next part is magic of Spring Data: you will never implement this interface yourself.
Saving objects to the database is handled by Hibernate.
Finding and deleting objects from the database is handled by Hibernate.
All this stuff works very similarly for each class you might have.
Spring Data will automatically implement the `PersonRepository` for you.

How can you use the repository that Spring implemented for you?
Here we will use another trick from Spring - dependency injection.
We will add a constructor to our web controller that has a parameter of type `PersonRepository`.
When the server starts up, then Spring will create an instance of your controller to serve the web requests.
When creating the instance, it will see the constructor parameter and search for a suitable argument for it - Spring Data can then provide the repository implementation it has generated.

This sample code would work:

```java
@Entity
public class Person {
    @Id
    @GeneratedValue
    private Long id;
}

public interface PersonRepository extends CrudRepository<Person, Long> {
}

@Controller
@Transactional
public class SampleController {

  private final PersonRepository persons;

  @Autowired
  public SampleController(PersonRepository persons) {
    this.persons = persons;
  }

  @RequestMapping("/")
  public void createPerson() {
    for (Person p : persons.findAll()) {
      // do something with the person
    }
  }
}
```

The methods in `CrudRepository` are not the only methods Spring Data can automatically implement.
Finding objects by some field value is also not very difficult.
Indeed, this will actually work:

```java
@Entity
public class Person {
    @Id
    @GeneratedValue
    private Long id;

    private String firstName;
    private String lastName;
}

public interface PersonRepository extends CrudRepository<Person, Long> {

  List<Person> findByFirstName(String name);

  Person findByFirstNameAndLastName(String first, String last);

}
```

In this example, Spring Data will try to implement the `PersonRepository` interface and find our findBy methods.
Next it will analyze the method names:
* it will ignore everything up to the "By" keyword: "findBy".
* next it will find "FirstName" - this matches a field name in the `Person` class
* "And" is a special keyword - in the "findByFirstNameAndLastName" case it will find "LastName" after "And", which also matches a field
* next it will try to find a parameter for each field that was referenced in the method name.
  here it will just look at the parameter order - "FirstName" is the first field in the name, thus the first parameter must be the value of firstName that we're looking for etc.
* finally, it will look at the return type - you can ask for a single object or a whole list

See the [Spring Data docs](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods) for all the keywords.

### Transactions and managed entities

Let's look at some more examples:

```java
Person p = new Person();
p.setFirstName("Bob");
p.setLastName("the First");
p = personRepository.save(p);
```

```java
Person p = personRepository.findByFirstNameAndLastName("Bob", "the First");
p.setLastName("the Second");
```

Here the first example saves a new Person object to the database using the `save` method.
The other example first fetches the same person and then changes his last name.
Note that `save` is not called after changing the name.
Is the change saved to the database?
Oddly, it is.

Internally, Hibernate keeps track of all the objects that it has seen.
It remembers each object that it returns through queries (find methods in the repositories) as well as any objects you save through the repositories.
You can imagine that Hibernate has a field `List<Object> managedObjects`.
Initially, the list is empty.
When you query and save objects using the repositories, all the used objects are placed in the `managedObjects` list.
Finally, when all the work is done, Hibernate inspects each object in the list and inserts/updates it in the database.

How does Hibernate know that all the work is done and the changes should be sent to the database?
Spring has an annotation `@Transactional`.
When a thread enters a method that has the `@Transactional` annotation, then Spring will tell Hibernate that the work has started.
When the thread exits the `@Transactional` method, then Spring will tell Hibernate that the work is done.
It's a good idea to mark all your controller methods `@Transactional`.
Instead of marking each method individually, you can also just add the annotation to the controller class.

## Task: migrate from files to the database

Time to get rid of the file reading/writing:
* Create the class `ForumPost` that contains an id and the text
* Create the class `ForumThread` that contains an id, thread name and the list of posts for that thread
* Create a repository for forum threads
* Replace file reading/writing code with the repository

Some hints to help you along:
* see the `SampleController` and other sample classes
* make sure you add all the proper annotations to your classes
* when Hibernate is very unhappy with something, deleting the database data files might help (forum-data.mv.db, forum-data.trace.db)
* thymeleaf can use fields in your objects: `${forumPost.text}`
* hibernate needs your entity classes to have a default constructor (constructor without parameters).
  you can still have other constructors.
* read the docs. not all will make sense, but there are good code examples.
  reading about the association annotations from the [Hibernate docs](https://docs.jboss.org/hibernate/orm/5.2/userguide/html_single/Hibernate_User_Guide.html) can be especially useful.
* usually you want to add `cascade = CascadeType.ALL` to your association annotations.
  this way when you save a forum thread, Hibernate will also save the forum posts attached to it.

## Task: add timestamps to the posts

It would be nice to know when each post was created.

* store the creation time as `java.time.Instant` in the post
* sort the posts before passing them to thymeleaf for rendering
* have thymeleaf render the creation time next to each post

## Task: add forum users

We will be adding support for user management step by step.
The first thing we do is store just the user's name.
Passwords and security will be added in the future tutorials.

* add a class `User` that has an id, email and display name
* add a page to the application that allows user registration:
  * user can submit the form with necessary information
  * data is saved to the database

## Task: link users to posts

Now that we have users, each post should be linked to one.
As we don't have login support yet, we will have to work around it.
We'll add proper login support in the next tutorial.

* each ForumPost object should reference it's author
* when writing a new post, let the user specify his email
* when rendering a post, show the user's display name

Some hints:
* save the post's author in a field of type User
* there's some new tricks hidden in the sample templates, in particular `th:field` in the edit template.
  it creates both the name and value attribute for the input.
  using it is optional, but recommended.

