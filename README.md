# RESTUtility

## Installation
Download the latest JAR file from here. _will add this link soon_

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
	
	@Column
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
Relations come in 2 forms, each can be configured differently. 
The first is a one-to-one relations; the one-to-one relations is identified by the property type. 
The second type is a one-to-many object relations; this is stored by a QueryResult object - this allows you to use it as a cursor for list views etc.

```Java
@Entity(requestKeyPath = "user", resourcePath = "users", table = "users")
public class User extends ManagedObject {

  	@Column(primaryKey = true, autoIncrement = false)
	public int id;
	
	@Column
	public String name;
	
	@Column(name = "creation_date", keyPath = "creation_date")
	public Date creationDate;
	
	@Relation
	public UserProfile profile;
	
	@Relation(model = Comment.class, connectedBy = "userid")
	public QueryResult comments;

}
```
* **keyPath**: The keyPath value is used to extract the object from the JSON object, if not defined the field name is used
* **name**: The database column defines the column name in the database, if not defined the field name is used
* **connectedBy**: The remote column name to reference the relationship with. If not defined, the default value is the local column name
* **model**: The model to reference in the relationship. This value is only used when defining a one-to-many relation and the field type is QueryResult
* **onDelete**: The action to perform when the remote object is deleted
* **onUpdate**: The action to perform when the remote object is updated
* **includeInRequest**: Indicates wether to send this relation when sending via the rest service - default is true


## Requesting Objects
When requesting object, you can use the standard HTTP verbs using the ObjectManager interface. It has support for GET, POST, PUT and DELETE.

```Java
User user = new User();

user.name = "Nick Babenko";
user.creationData = new Date();

ObjectManager.instance().postObject(user, "user", new ObjectRequestListener() {

	@Override
	public void success(MappingResult mappingResult) {
		mappingResult.loadCursorForManagedObjectReference();
		
		if(mappingResult.count() > 0) {
			User user = (User) mappingResult.firstObject();
			
			Intent intent = new Intent(context, UserProfileActivity.class);
			
			intent.putParcelableExtra(Key_User, user);
			
			startActivity(userIntent);
		}
	}
	
	@Override
	public void failure(int status) {
		new AlertDialog.Builder(context)
			.setTitle("Registration")
			.setMessage("Registration failed")
			.setNeutralButton("OK", null)
			.show();
	}
}, "user");
```

The mapping result contains a cursor referenced to the response objects. By default, it's not loaded - but count still returns a valid property. This is to improve mapping speed for large requests.
To initialize the cursor, call the ```loadCursorForManagedObjectReference``` method. The MappingResult class interfaces cursor fully.


## Querying Objects
Queries are done using the Query class. It support chaining, query helpers and raw query support.

```Java
QueryResult queryResult = new Query(Type.Select)
	.from(User.class)
	.where("name", "=", "Nick Babenko")
	.execute();
	
if(queryResult.count() > 0) {
	User user = (User) queryResult.current();
}
```
