package dbConnectors;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Abstracts SQL connections
 * @author William Falcon
 *
 */
public class MYSQLConnector {

	//DB settings
	private static final String DB_USER_NAME = "root";
	private static final String USER_PASSWORD = "";
	private static final String CONNECTION_URL = "jdbc:mysql://localhost:3306/";
	private static final String DB_NAME = "Sample";

	//JDBC Driver
	private static final String DRIVER = "com.mysql.jdbc.Driver";


	/********************************************************************************
	 **************************************ADMIN*************************************
	 ********************************************************************************/

	/**
	 * Class constructor
	 */
	public MYSQLConnector(){

		//Connect JDBC driver
		connectJDBCDriver();
	}

	/**
	 * Connects JDBC driver
	 */
	private void connectJDBCDriver(){
		try {
			//Connect
			Class.forName(DRIVER).newInstance();
			System.out.println("Driver started succesfully");
			System.out.println("Connected to MYSQL database");

		} catch (ClassNotFoundException e) {

			// Error message
			e.printStackTrace();
		} catch (InstantiationException e) {

			e.printStackTrace();
		} catch (IllegalAccessException e) {

			e.printStackTrace();
		}
	}

	/********************************************************************************
	 ******************************CONVENIENCE METHODS*******************************
	 ********************************************************************************/

	/**
	 * Executes a SQL query and returns an arrayList of objects from the result
	 * @param sqlQuery
	 * @param Object template
	 * @return arrayList of objects
	 * @author William Falcon
	 */
	public ArrayList<Object> getObjectResultsFromQuery(String sqlQuery, Object template) throws Exception{

		//Init results
		ArrayList<Object> results = new ArrayList<Object>();

		//Connect and execute query
		Connection connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);
		Statement statement = connection.createStatement();

		//Handle queries without results needed
		if (template==null) {

			if (sqlQuery.contains("DELETE")) {
				statement.executeUpdate(sqlQuery);
				return null;
			}

			statement.executeQuery(sqlQuery);
			return null;
		}

		//execute sql query
		ResultSet resultSet = statement.executeQuery(sqlQuery);

		Class<?> newClass = Class.forName(template.getClass().getName());
		Object result;
		
		//Extract results
		while (resultSet.next()) {

			//Get object back
			result = newClass.newInstance();
			result = MYSQLParser.mapSQLResultToObject(result, resultSet);

			//Add to results
			results.add(result);
		}

		//Close resources
		connection.close();
		statement.close();
		resultSet.close();

		//Return results
		return results;
	}

	/**
	 * Executes any sql query.
	 * Takes an array of parameters to apply to query.
	 * Query should have a question mark in every parameter.
	 * 
	 * Sample use:  sql.executeGenericQuery("SELECT * FROM db.user WHERE name LIKE ?", new ArrayList(Arrays.asList("bob")));
	 * 
	 * @param sqlQuery
	 * @param preparedStatementParameters
	 * @return
	 * @throws Exception
	 */
	public ArrayList<Object> executeGenericQuery(String sqlQuery, ArrayList<String> preparedStatementParameters) throws Exception{

		//Standardize query
		sqlQuery = sqlQuery.toLowerCase();
		
		ArrayList<Object> results = new ArrayList<Object>();
		ResultSet resultSet = null;
		Connection connection = null;
		PreparedStatement statement = null;
		
		try {
			//Connect and execute query
			connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);
			statement = connection.prepareStatement(sqlQuery);

			//Add the parameter values to the statement
			if (preparedStatementParameters!=null) {
				for (int i = 0; i < preparedStatementParameters.size(); i++) {
					statement.setString(i+1, preparedStatementParameters.get(i));
				}
			}

			//Handle all other cases except select
			if (!sqlQuery.contains("select")) {
				statement.executeUpdate();

				//Handle select statement
			}else {
				//execute sql query
				resultSet = statement.executeQuery();

				//Parse results
				results = MYSQLParser.resultSetToArrayList(resultSet);
			}
			
		}catch (Exception e) {
			
			//Pass the exception up
			throw e;
			
		}finally{
			
			//Close resources whether successful or not
			if (connection!=null) {
				connection.close();
			}
			
			if (statement!=null) {
				statement.close();
			}
			
			if (resultSet!=null) {
				resultSet.close();
			}
		}
		
		//Return results
		return results;
	}

	/**
	 * Inserts list of objects into a specific table
	 * @param tableName
	 * @param objects
	 * @throws Exception
	 * @author waf04
	 */
	public void insertObjectList(String tableName, ArrayList<?> objects) throws Exception{

		//Init connection
		Connection connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);

		int count =0;

		//Iterate over objects
		for (Object object : objects) {

			//Track progress
			count ++;
			System.out.println(count+"/"+objects.size());

			//Insert
			insertObject(object, tableName, connection);
		}

		//Close resources
		connection.close();
	}


	/**
	 * Inserts any object into SQL using the corresponding table
	 * @param tableName
	 * @param object
	 * @throws Exception
	 * @author waf04
	 */
	public void insertObject(String tableName, Object object) throws Exception{

		//Create connection
		Connection connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);

		//Insert object
		insertObject(object, tableName, connection);

		//Close resources
		connection.close();
	}

	/**
	 * Inserts ANY object into the database using it's own prepared statement
	 * It uses reflection to create the query and the prepared statement
	 * 
	 * myId will NOT be inserted (allow auto increment to do its thing)
	 * @param object
	 * @return
	 * @author waf04
	 */
	private void insertObject(Object object, String tableName, Connection connection) throws Exception{

		//Get the object's fields
		Field[] objectFields = object.getClass().getDeclaredFields();

		//Set which fields are not relationships
		ArrayList<String>allowedFields = new ArrayList<String>();
		allowedFields.add("int");
		allowedFields.add("java.lang.String");
		allowedFields.add("double");
		allowedFields.add("java.lang.Double");
		allowedFields.add("java.lang.Integer");

		//Only the fields to insert for the object
		ArrayList<Field> mainObjectQueryFields = new ArrayList<Field>();

		String type;
		//Remove all array types
		for (Field field : objectFields) {

			//Get the name of that type
			type = field.getType().getName();

			//If the array has the field, add to array for main insert
			if (allowedFields.contains(type) && field.getName()!="myId") {
				mainObjectQueryFields.add(field);
			}
		}

		//Create joint columns string (id, name, etc...) and (?,?)
		String columns = new String();
		String questionMarks = new String();
		Field field = null;
		
		//Iterate over fields
		for (int i=0; i<mainObjectQueryFields.size(); i++) {

			//Get field
			field = mainObjectQueryFields.get(i);

			//If the last object don't add a comma
			if (i==mainObjectQueryFields.size()-1) {
				columns+= field.getName();
				questionMarks += "?";
			}else {

				//Append to string
				columns += field.getName() + ", ";
				questionMarks += "?,";
			}
		}

		//Make private connection
		Connection privateConnection = connection;

		//Create prepared statement
		PreparedStatement genericStatement = privateConnection.prepareStatement("INSERT INTO " + tableName + " (" + columns + ") VALUES (" +questionMarks+ ")");

		Object value = null;
		
		//For each field add to prepared statement
		for (int i = 0; i < mainObjectQueryFields.size(); i++) {

			//Get the field
			field = mainObjectQueryFields.get(i);
			field.setAccessible(true);

			//Get the value at that field
			value = field.get(object);

			//Set the object at that prepared statement index
			genericStatement.setObject(i+1, value);
		}

		//Execute the prepared statement
		genericStatement.execute();

		//Close resources
		genericStatement.close();
	}

	/**
	 * Updates an object in the specific table
	 * @param tableName
	 * @param object
	 * @throws Exception
	 */
	public void updateObject(String tableName, Object object, Connection connection) throws Exception{

		//Insert object
		updateObject(object, tableName, connection);
	}

	/**
	 * Updates an object in the specific table
	 * @param tableName
	 * @param object
	 * @throws Exception
	 */
	public void updateObject(String tableName, Object object) throws Exception{

		//Init connection
		Connection connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);

		//Insert object
		updateObject(object, tableName, connection);

		//Close resources
		connection.close();
	}

	/**
	 * Updates a list of objects in a specific table
	 * @param tableName
	 * @param objects
	 * @throws Exception
	 */
	public void updateObjectList(String tableName, ArrayList<?> objects) throws Exception{

		//Init connection
		Connection connection = DriverManager.getConnection(CONNECTION_URL+DB_NAME,DB_USER_NAME,USER_PASSWORD);

		int count =0;
		//Iterate and insert
		for (Object object : objects) {
			count++;
			System.out.println(count);
			updateObject(object, tableName, connection);
		}

		//Close resources
		connection.close();
	}

	/**
	 * Inserts ANY object into the database using it's own prepared statement
	 * It uses reflection to create the query and the prepared statement
	 * 
	 * myId will NOT be inserted (allow auto increment to do its thing)
	 * @param object
	 * @return
	 * @author waf04
	 */
	private void updateObject(Object object, String tableName, Connection connection) throws Exception{

		//Get the object's fields
		Field[] objectFields = object.getClass().getDeclaredFields();

		//Set which fields are not relationships
		ArrayList<String>allowedFields = new ArrayList<String>();
		allowedFields.add("int");
		allowedFields.add("java.lang.String");
		allowedFields.add("double");
		allowedFields.add("java.lang.Double");
		allowedFields.add("java.lang.Integer");

		//Only the fields to insert for the object
		ArrayList<Field> mainObjectQueryFields = new ArrayList<Field>();

		//Init myId
		String myId = new String();
		String type = new String();
		
		//Remove all array types
		for (Field field : objectFields) {

			field.setAccessible(true);
			//Get the name of that type
			type = field.getType().getName();

			//If the array has the field, add to array for main insert
			if (allowedFields.contains(type) && !field.getName().equals("myId")) {
				mainObjectQueryFields.add(field);

			}else if (field.getName().equals("myId")) {
				//Get myId
				Integer myIdInt = field.getInt(object);
				myId = Integer.toString(myIdInt);
			}
		}

		//Create joint columns string (id, name, etc...) and (?,?)
		String columns = new String();

		Field field = null;
		
		//Iterate over fields
		for (int i=0; i<mainObjectQueryFields.size(); i++) {

			//Get field
			field = mainObjectQueryFields.get(i);

			//If the last object don't add a comma
			if (i==mainObjectQueryFields.size()-1) {
				columns+= field.getName()+" = ?";

			}else {

				//Append to string
				columns += field.getName() + " = ? , ";

			}
		}

		//Make private connection
		Connection privateConnection = connection;

		//Create prepared statement
		PreparedStatement genericStatement = privateConnection.prepareStatement("UPDATE " + tableName + "  SET " + columns + " WHERE myId = "+myId);

		Object value = new Object();
		
		//For each field add to prepared statement
		for (int i = 0; i < mainObjectQueryFields.size(); i++) {

			//Get the field
			mainObjectQueryFields.get(i);
			field.setAccessible(true);

			//Get the value at that field
			value = field.get(object);

			//Set the object at that prepared statement index
			genericStatement.setObject(i+1, value);
		}

		//Execute the prepared statement
		genericStatement.execute();

		//Close resources
		genericStatement.close();
	}
}
