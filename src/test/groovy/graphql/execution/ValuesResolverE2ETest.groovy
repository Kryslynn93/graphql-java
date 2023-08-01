package graphql.execution

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.Scalars
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class ValuesResolverE2ETest extends Specification {

    def "3276 - reported bug on validation problems as SDL"() {
        def sdl = '''
            type Query {
              items(pagination: Pagination = {limit: 10, offset: 0}): [String]
            }
            
            input Pagination {
              limit: Int
              offset: Int
            }
        '''
        DataFetcher df = { DataFetchingEnvironment env ->
            def pagination = env.getArgument("pagination") as Map<String, Integer>
            def strings = pagination.entrySet().collect { entry -> entry.key + "=" + entry.value }
            return strings
        }
        def schema = TestUtil.schema(sdl, [Query: [items: df]])
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def ei = ExecutionInput.newExecutionInput('''
            query Items($limit: Int, $offset: Int) {
                 items(pagination: {limit: $limit, offset: $offset})
             }
            ''').variables([limit: 5, offset: 0]).build()
        def er = graphQL.execute(ei)
        then:
        er.errors.isEmpty()
        er.data == [items : ["limit=5", "offset=0"]]
    }

    def "3276 - reported bug on validation problems as reported code"() {
        DataFetcher<?> dataFetcher = { env -> Collections.singletonList("test") }
        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(GraphQLObjectType.newObject()
                        .name("Query")
                        .field(items -> items
                                .name("items")
                                .type(GraphQLList.list(Scalars.GraphQLString))
                                .argument(pagination -> pagination
                                        .name("pagination")
                                //skipped adding the default limit/offset values as it doesn't change anything
                                        .defaultValueProgrammatic(new HashMap<>())
                                        .type(GraphQLInputObjectType.newInputObject()
                                                .name("Pagination")
                                                .field(limit -> limit
                                                        .name("limit")
                                                        .type(Scalars.GraphQLInt))
                                                .field(offset -> offset
                                                        .name("offset")
                                                        .type(Scalars.GraphQLInt))
                                                .build())))
                        .build())
                .codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                        .dataFetcher(FieldCoordinates.coordinates("Query", "items"), dataFetcher)
                        .build())
                .build()

        GraphQL gql = GraphQL.newGraphQL(schema).build();

        Map<String, Object> vars = new HashMap<>();
        vars.put("limit", 5)
        vars.put("offset", 0)

        ExecutionInput ei = ExecutionInput.newExecutionInput()
                .query("query Items( \$limit: Int, \$offset: Int) {\n" +
                        "  items(\n" +
                        "    pagination: {limit: \$limit, offset: \$offset} \n" +
                        "  )\n" +
                        "}")
                .variables(vars)
                .build()

        when:
        ExecutionResult result = gql.execute( ei)
        then:
        result.errors.isEmpty()
        result.data == [items : ["test"]]
    }
}
