# RESTUtility

## Installation
Download the latest JAR file from here.

## Setup
To initialize RESTUtility in your application - on launch, via the ```App.onCreate``` method call ```ObjectManager.init(Context context, String baseUrl, String dbName, String dbVersion)```.

## Creating Models
### Model object
Create a class and have it subclass ManagedObject. Give the class an ```Entity``` annotation.

```Java
@Entity(requestKeyPath = "user", resourcePath = "users", table = "users")
public class User extends ManagedObject { }
```
* **requestKeyPath**: This is used as a root key path when PUTing or POSTing the object. It will prefix it as an array key.
* **resourcePath**: This is the resourcePath, which can be used during a GET request.
* **table**: an optional table definition - if not set, the class name will be used.

### Properties
In your model add public properties (private or protected will not work as expected) with the appropriate type. 

For number types, you can use Objects to prevent null values from being sent to REST service e.g. Double instead of double, Integer instead of int etc.

Calendar and Date types will also be converted and a specific conversion type can be defined.

Give each property a ```Column``` annotation.

```Java
@Entity(requestKeyPath = "user", resourcePath = "users", table = "users")
public class User extends ManagedObject {

  @Column(primaryKey = true, autoIncrement = false)
	public int id;
	
	@Column(name = "email")
	public String name;
	
	@Column(name = "creation_date", keyPath = "creation_date")
	public Date creationDate;

}
```
* **autoIncrement**: If true, the value will be auto incremented. If the property type isn't an integer, it will be ignored will not auto increment.
* **primaryKey**: This defines the primary key. It can be any value, regardless of type but int types can be auto incremented, if required.
* **name**: The name property defines the database column name. If not set, the property name will be used.
* **keyPath**: The keyPath property is used to extract a value from the REST response. If not set the property name will be used. If it doesn't exist, the value will be null.* 
* **length**: The length property is used to define the database column length.
* **dateFormat**: This is the format of the REST date format, it will be parsed from any data received and translated back for any data sent. Possible values: DateFormat.Default, DateFormat.Unix, DateFormat.Mysql_Date, DateFormat.Mysql_DateTime.
* **ignoreIfZero**: This is for int and float types. If they value is 0, they won't be sent via requests.

### Relations
