use dynamo::model::{AttributeDefinition, KeySchemaElement, KeyType, ScalarAttributeType, ProvisionedThroughput};
use dynamo::operation::CreateTable;
use dynamo::output::{ListTablesOutput };
use std::error::Error;

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    let table_name = "new_table";
    let client = io_v0::Client::local("dynamodb");
    let clear_table = dynamo::operation::DeleteTable::builder()
        .table_name(table_name)
        .build();
    match io_v0::dispatch!(client, clear_table).parsed() {
        Ok(table_deleted) => println!("{:?} table was deleted", table_deleted),
        Err(e) => println!("dispatch error: {:?}", e),
    }

    let tables = io_v0::dispatch!(client, dynamo::operation::ListTables::builder().build()).parsed.unwrap();
    assert_eq!(
        tables.unwrap(),
        ListTablesOutput::builder().table_names(vec![]).build()
    );

    let create_table = CreateTable::builder()
        .table_name(table_name)
        .attribute_definitions(vec![AttributeDefinition::builder()
            .attribute_name("ForumName")
            .attribute_type(ScalarAttributeType::from("S"))
            .build()])
        .key_schema(vec![KeySchemaElement::builder()
            .attribute_name("ForumName")
            .key_type(KeyType::from("HASH"))
            .build()])
        .provisioned_throughput(ProvisionedThroughput::builder().read_capacity_units(100).write_capacity_units(100).build())
        .build();

    let response = io_v0::dispatch!(client, create_table);
    match response.parsed {
        Some(Ok(output)) => assert_eq!(output.table_description.unwrap().table_name.unwrap(), table_name),
        _ => println!("{:?}", response.raw)
    }

    let tables = io_v0::dispatch!(client, dynamo::operation::ListTables::builder().build()).parsed.unwrap();
    assert_eq!(
        tables.unwrap(),
        ListTablesOutput::builder().table_names(vec![table_name.to_string()]).build()
    );

    //assert_eq!(table_created.table_description.unwrap().table_name.unwrap(), "new table");
    Ok(())
}