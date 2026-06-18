package io.github.hectorvent.floci.services.s3vectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsErrorResponse;
import io.github.hectorvent.floci.services.s3vectors.model.VectorBucket;
import io.github.hectorvent.floci.services.s3vectors.model.VectorIndex;
import io.github.hectorvent.floci.services.s3vectors.model.VectorData;
import io.quarkus.runtime.annotations.RegisterForReflection;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class S3VectorsJsonHandler {

    private final S3VectorsService service;
    private final ObjectMapper objectMapper;

    @Inject
    public S3VectorsJsonHandler(S3VectorsService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    public Response handle(String action, JsonNode request, String region) {
        return switch (action) {
            case "CreateVectorBucket" -> handleCreateVectorBucket(request, region);
            case "GetVectorBucket" -> handleGetVectorBucket(request, region);
            case "ListVectorBuckets" -> handleListVectorBuckets(request, region);
            case "DeleteVectorBucket" -> handleDeleteVectorBucket(request, region);

            case "CreateIndex" -> handleCreateIndex(request, region);
            case "GetIndex" -> handleGetIndex(request, region);
            case "ListIndexes" -> handleListIndexes(request, region);
            case "DeleteIndex" -> handleDeleteIndex(request, region);

            case "PutVectors" -> handlePutVectors(request, region);
            case "GetVectors" -> handleGetVectors(request, region);
            case "DeleteVectors" -> handleDeleteVectors(request, region);
            case "QueryVectors" -> handleQueryVectors(request, region);

            default -> Response.status(400)
                    .entity(new AwsErrorResponse("UnsupportedOperation", "Operation " + action + " is not supported."))
                    .build();
        };
    }

    // ── Response Mappings ──────────────────────────────────────────────────

    @RegisterForReflection
    public record CreateVectorBucketResponse(
            @JsonProperty("VectorBucket") VectorBucketResponse bucket
    ) {}

    @RegisterForReflection
    public record VectorBucketResponse(
            @JsonProperty("VectorBucketName") String vectorBucketName,
            @JsonProperty("VectorBucketArn") String vectorBucketArn,
            @JsonProperty("EncryptionConfiguration") Object encryptionConfiguration
    ) {}

    @RegisterForReflection
    public record ListVectorBucketsResponse(
            @JsonProperty("VectorBuckets") List<VectorBucketResponse> vectorBuckets
    ) {}

    @RegisterForReflection
    public record CreateIndexResponse(
            @JsonProperty("Index") IndexResponse index
    ) {}

    @RegisterForReflection
    public record IndexResponse(
            @JsonProperty("IndexName") String indexName,
            @JsonProperty("IndexArn") String indexArn,
            @JsonProperty("VectorBucketName") String vectorBucketName,
            @JsonProperty("Dimension") int dimension,
            @JsonProperty("DataType") String dataType,
            @JsonProperty("DistanceMetric") String distanceMetric
    ) {}

    @RegisterForReflection
    public record ListIndexesResponse(
            @JsonProperty("Indexes") List<IndexResponse> indexes
    ) {}

    // ── Handlers ───────────────────────────────────────────────────────────

    private Response handleCreateVectorBucket(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        Object encryption = request.has("EncryptionConfiguration") ? request.get("EncryptionConfiguration") : null;

        VectorBucket bucket = service.createVectorBucket(bucketName, encryption, region);
        VectorBucketResponse res = new VectorBucketResponse(bucket.getVectorBucketName(), bucket.getVectorBucketArn(), bucket.getEncryptionConfiguration());
        return Response.ok(new CreateVectorBucketResponse(res)).build();
    }

    private Response handleGetVectorBucket(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        VectorBucket bucket = service.getVectorBucket(bucketName, region);
        VectorBucketResponse res = new VectorBucketResponse(bucket.getVectorBucketName(), bucket.getVectorBucketArn(), bucket.getEncryptionConfiguration());
        return Response.ok(new CreateVectorBucketResponse(res)).build();
    }

    private Response handleListVectorBuckets(JsonNode request, String region) {
        List<VectorBucket> list = service.listVectorBuckets(region);
        List<VectorBucketResponse> responseList = list.stream()
                .map(b -> new VectorBucketResponse(b.getVectorBucketName(), b.getVectorBucketArn(), b.getEncryptionConfiguration()))
                .toList();
        return Response.ok(new ListVectorBucketsResponse(responseList)).build();
    }

    private Response handleDeleteVectorBucket(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        service.deleteVectorBucket(bucketName, region);
        return Response.ok(objectMapper.createObjectNode()).build();
    }

    private Response handleCreateIndex(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();
        int dimension = request.path("Dimension").asInt();
        String dataType = request.path("DataType").asText("float32");
        String distanceMetric = request.path("DistanceMetric").asText("COSINE");

        List<String> nonFilterable = new ArrayList<>();
        if (request.has("NonFilterableMetadataKeys")) {
            request.path("NonFilterableMetadataKeys").forEach(n -> nonFilterable.add(n.asText()));
        }

        VectorIndex index = service.createIndex(bucketName, indexName, dimension, dataType, distanceMetric, nonFilterable, region);
        IndexResponse res = new IndexResponse(index.getIndexName(), index.getIndexArn(), index.getVectorBucketName(), index.getDimension(), index.getDataType(), index.getDistanceMetric());
        return Response.ok(new CreateIndexResponse(res)).build();
    }

    private Response handleGetIndex(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();

        VectorIndex index = service.getIndex(bucketName, indexName, region);
        IndexResponse res = new IndexResponse(index.getIndexName(), index.getIndexArn(), index.getVectorBucketName(), index.getDimension(), index.getDataType(), index.getDistanceMetric());
        return Response.ok(new CreateIndexResponse(res)).build();
    }

    private Response handleListIndexes(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        List<VectorIndex> list = service.listIndexes(bucketName, region);
        List<IndexResponse> responseList = list.stream()
                .map(i -> new IndexResponse(i.getIndexName(), i.getIndexArn(), i.getVectorBucketName(), i.getDimension(), i.getDataType(), i.getDistanceMetric()))
                .toList();
        return Response.ok(new ListIndexesResponse(responseList)).build();
    }

    private Response handleDeleteIndex(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();
        service.deleteIndex(bucketName, indexName, region);
        return Response.ok(objectMapper.createObjectNode()).build();
    }

    private Response handlePutVectors(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();

        List<VectorData> vectors = new ArrayList<>();
        JsonNode vectorsNode = request.path("Vectors");
        if (vectorsNode.isArray()) {
            for (JsonNode vNode : vectorsNode) {
                String key = vNode.path("Key").asText();
                List<Float> floatList = new ArrayList<>();
                JsonNode float32Node = vNode.path("Data").path("Float32");
                if (float32Node.isArray()) {
                    float32Node.forEach(f -> floatList.add((float) f.asDouble()));
                }
                Map<String, Object> metadata = new HashMap<>();
                if (vNode.has("Metadata")) {
                    metadata = objectMapper.convertValue(vNode.get("Metadata"), new TypeReference<Map<String, Object>>() {});
                }
                vectors.add(new VectorData(key, floatList, metadata));
            }
        }

        service.putVectors(bucketName, indexName, vectors, region);
        return Response.ok(objectMapper.createObjectNode()).build();
    }

    private Response handleGetVectors(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();

        List<String> keys = new ArrayList<>();
        if (request.has("Keys")) {
            request.path("Keys").forEach(k -> keys.add(k.asText()));
        }

        List<VectorData> vectors = service.getVectors(bucketName, indexName, keys, region);

        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode vectorsArray = objectMapper.createArrayNode();
        for (VectorData v : vectors) {
            ObjectNode vNode = objectMapper.createObjectNode();
            vNode.put("Key", v.getKey());
            ObjectNode dataNode = objectMapper.createObjectNode();
            ArrayNode floatNode = objectMapper.createArrayNode();
            v.getData().forEach(floatNode::add);
            dataNode.set("Float32", floatNode);
            vNode.set("Data", dataNode);
            vNode.set("Metadata", objectMapper.valueToTree(v.getMetadata()));
            vectorsArray.add(vNode);
        }
        response.set("Vectors", vectorsArray);
        return Response.ok(response).build();
    }

    private Response handleDeleteVectors(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();

        List<String> keys = new ArrayList<>();
        if (request.has("Keys")) {
            request.path("Keys").forEach(k -> keys.add(k.asText()));
        }

        service.deleteVectors(bucketName, indexName, keys, region);
        return Response.ok(objectMapper.createObjectNode()).build();
    }

    private Response handleQueryVectors(JsonNode request, String region) {
        String bucketName = request.path("VectorBucketName").asText();
        String indexName = request.path("IndexName").asText();
        int topK = request.path("TopK").asInt(10);

        List<Float> queryVector = new ArrayList<>();
        JsonNode float32Node = request.path("Vector").path("Float32");
        if (float32Node.isArray()) {
            float32Node.forEach(f -> queryVector.add((float) f.asDouble()));
        }

        List<S3VectorsService.QueryResult> results = service.queryVectors(bucketName, indexName, queryVector, topK, region);

        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode vectorsArray = objectMapper.createArrayNode();
        for (S3VectorsService.QueryResult res : results) {
            VectorData v = res.getVector();
            ObjectNode vNode = objectMapper.createObjectNode();
            vNode.put("Key", v.getKey());
            ObjectNode dataNode = objectMapper.createObjectNode();
            ArrayNode floatNode = objectMapper.createArrayNode();
            v.getData().forEach(floatNode::add);
            dataNode.set("Float32", floatNode);
            vNode.set("Data", dataNode);
            vNode.set("Metadata", objectMapper.valueToTree(v.getMetadata()));
            vNode.put("Distance", res.getDistance());
            vectorsArray.add(vNode);
        }
        response.set("Vectors", vectorsArray);
        return Response.ok(response).build();
    }
}
