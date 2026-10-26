package com.smms.assistance.infrastructure.client;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import java.util.List;
import java.util.UUID;
import com.smms.assistance.infrastructure.controller.graphql.dto.*;

@GraphQLClientApi(configKey = "assistance-service")
public interface AssistanceClient {

    @Query("listTeamAttendancePM")
    AttendancePageOutput listTeamAttendancePM(
            @Name("userIds") List<UUID> userIds,
            @Name("projectId") UUID projectId,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("status") String status,
            @Name("page") int page,
            @Name("size") int size
    );

    @Query("listTeamJustificationsPM")
    JustificationPageOutput listTeamJustificationsPM(
            @Name("userIds") List<UUID> userIds,
            @Name("status") String status,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate,
            @Name("page") int page,
            @Name("size") int size
    );

    @Query("getJustificationDetailPM")
    JustificationOutput getJustificationDetailPM(@Name("id") UUID id);

    @Query("getDashboardPMInternal")
    DashboardOutput getDashboardPMInternal(
            @Name("userIds") List<UUID> userIds,
            @Name("projectId") UUID projectId,
            @Name("fromDate") String fromDate,
            @Name("toDate") String toDate
    );

    @Mutation("approveJustificationPM")
    JustificationOutput approveJustificationPM(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    );

    @Mutation("rejectJustificationPM")
    JustificationOutput rejectJustificationPM(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    );

    @Mutation("requestObservationPM")
    JustificationOutput requestObservationPM(
            @Name("justificationId") UUID justificationId,
            @Name("comment") String comment
    );
}
