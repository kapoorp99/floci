package io.github.hectorvent.floci.services.s3vectors;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3VectorsIntegrationTest {

    private static final String S3V_CONTENT_TYPE = "application/x-amz-json-1.1";
    private static final String BUCKET_NAME = "my-vector-bucket";
    private static final String INDEX_NAME = "my-vector-index";

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    @Order(1)
    void createVectorBucket() {
        given()
            .header("X-Amz-Target", "S3Vectors.CreateVectorBucket")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s"
                }
                """.formatted(BUCKET_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("VectorBucket.VectorBucketName", equalTo(BUCKET_NAME))
            .body("VectorBucket.VectorBucketArn", containsString("arn:aws:s3vectors:"));
    }

    @Test
    @Order(2)
    void getVectorBucket() {
        given()
            .header("X-Amz-Target", "S3Vectors.GetVectorBucket")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s"
                }
                """.formatted(BUCKET_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("VectorBucket.VectorBucketName", equalTo(BUCKET_NAME));
    }

    @Test
    @Order(3)
    void listVectorBuckets() {
        given()
            .header("X-Amz-Target", "S3Vectors.ListVectorBuckets")
            .contentType(S3V_CONTENT_TYPE)
            .body("{}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("VectorBuckets", notNullValue())
            .body("VectorBuckets.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(4)
    void createIndex() {
        given()
            .header("X-Amz-Target", "S3Vectors.CreateIndex")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Dimension": 3,
                    "DistanceMetric": "COSINE"
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Index.IndexName", equalTo(INDEX_NAME))
            .body("Index.Dimension", equalTo(3))
            .body("Index.DistanceMetric", equalTo("COSINE"));
    }

    @Test
    @Order(5)
    void getIndex() {
        given()
            .header("X-Amz-Target", "S3Vectors.GetIndex")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s"
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Index.IndexName", equalTo(INDEX_NAME));
    }

    @Test
    @Order(6)
    void listIndexes() {
        given()
            .header("X-Amz-Target", "S3Vectors.ListIndexes")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s"
                }
                """.formatted(BUCKET_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Indexes", notNullValue())
            .body("Indexes.size()", greaterThanOrEqualTo(1));
    }

    @Test
    @Order(7)
    void putVectors() {
        given()
            .header("X-Amz-Target", "S3Vectors.PutVectors")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Vectors": [
                        {
                            "Key": "v1",
                            "Data": {
                                "Float32": [1.0, 0.0, 0.0]
                            },
                            "Metadata": {
                                "label": "first"
                            }
                        },
                        {
                            "Key": "v2",
                            "Data": {
                                "Float32": [0.0, 1.0, 0.0]
                            },
                            "Metadata": {
                                "label": "second"
                            }
                        }
                    ]
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(8)
    void getVectors() {
        given()
            .header("X-Amz-Target", "S3Vectors.GetVectors")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Keys": ["v1", "v2"]
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Vectors", hasSize(2))
            .body("Vectors.find { it.Key == 'v1' }.Data.Float32", contains(1.0f, 0.0f, 0.0f))
            .body("Vectors.find { it.Key == 'v2' }.Metadata.label", equalTo("second"));
    }

    @Test
    @Order(9)
    void queryVectorsCosine() {
        given()
            .header("X-Amz-Target", "S3Vectors.QueryVectors")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Vector": {
                        "Float32": [1.0, 0.1, 0.0]
                    },
                    "TopK": 1
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Vectors", hasSize(1))
            .body("Vectors[0].Key", equalTo("v1"))
            .body("Vectors[0].Distance", notNullValue());
    }

    @Test
    @Order(10)
    void deleteVectors() {
        given()
            .header("X-Amz-Target", "S3Vectors.DeleteVectors")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Keys": ["v1"]
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200);

        // Verify key is gone
        given()
            .header("X-Amz-Target", "S3Vectors.GetVectors")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s",
                    "Keys": ["v1"]
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Vectors", hasSize(0));
    }

    @Test
    @Order(11)
    void deleteIndex() {
        given()
            .header("X-Amz-Target", "S3Vectors.DeleteIndex")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s"
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200);

        // Verify index is gone
        given()
            .header("X-Amz-Target", "S3Vectors.GetIndex")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s",
                    "IndexName": "%s"
                }
                """.formatted(BUCKET_NAME, INDEX_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(12)
    void deleteVectorBucket() {
        given()
            .header("X-Amz-Target", "S3Vectors.DeleteVectorBucket")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s"
                }
                """.formatted(BUCKET_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(200);

        // Verify bucket is gone
        given()
            .header("X-Amz-Target", "S3Vectors.GetVectorBucket")
            .contentType(S3V_CONTENT_TYPE)
            .body("""
                {
                    "VectorBucketName": "%s"
                }
                """.formatted(BUCKET_NAME))
        .when()
            .post("/")
        .then()
            .statusCode(404);
    }
}
