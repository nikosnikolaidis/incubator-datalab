/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.epam.dlab.backendapi.util;

import com.epam.dlab.backendapi.domain.BillingReportLine;
import com.epam.dlab.dto.UserInstanceDTO;
import com.epam.dlab.dto.UserInstanceStatus;
import com.epam.dlab.dto.base.DataEngineType;
import com.epam.dlab.dto.billing.BillingResourceType;
import com.epam.dlab.dto.computational.UserComputationalResource;
import jersey.repackaged.com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class BillingUtils {

    private static final String[] REPORT_HEADERS = {"DLab ID", "User", "Project", "DLab Resource Type", "Shape", "Product", "Cost"};
    private static final String REPORT_FIRST_LINE = "Service base name: %s. Available reporting period from: %s to: %s";
    private static final String TOTAL_LINE = "Total: %s %s";
    private static final String EDGE_FORMAT = "%s-%s-%s-edge";
    private static final String EDGE_VOLUME_FORMAT = "%s-%s-%s-edge-volume-primary";
    private static final String EDGE_BUCKET_FORMAT = "%s-%s-bucket";
    private static final String VOLUME_PRIMARY_FORMAT = "%s-volume-primary";
    private static final String VOLUME_SECONDARY_FORMAT = "%s-volume-secondary";
    private static final String VOLUME_PRIMARY = "Volume primary";
    private static final String VOLUME_SECONDARY = "Volume secondary";
    private static final String SHARED_RESOURCE = "Shared resource";

    private static final String DATAENGINE_NAME_FORMAT = "%d x %s";
    private static final String DATAENGINE_SERVICE_NAME_FORMAT = "Master: %s%sSlave:  %d x %s";

    public static Stream<BillingReportLine> edgeBillingDataStream(String project, String sbn, String endpoint, String status) {
        final String userEdgeId = String.format(EDGE_FORMAT, sbn, project.toLowerCase(), endpoint);
        final String edgeVolumeId = String.format(EDGE_VOLUME_FORMAT, sbn, project.toLowerCase(), endpoint);
        final String edgeBucketId = String.format(EDGE_BUCKET_FORMAT, sbn, project.toLowerCase());
        return Stream.of(
                BillingReportLine.builder().resourceName("EDGE node").user(SHARED_RESOURCE).project(project).dlabId(userEdgeId).resourceType(BillingResourceType.EDGE)
                        .status(UserInstanceStatus.of(status)).build(),
                BillingReportLine.builder().resourceName("EDGE volume").user(SHARED_RESOURCE).project(project).dlabId(edgeVolumeId).resourceType(BillingResourceType.VOLUME).build(),
                BillingReportLine.builder().resourceName("EDGE bucket").user(SHARED_RESOURCE).project(project).dlabId(edgeBucketId).resourceType(BillingResourceType.EDGE_BUCKET).build()
        );
    }

    public static Stream<BillingReportLine> ssnBillingDataStream(String sbn) {
        final String ssnId = sbn + "-ssn";
        final String bucketName = sbn.replaceAll("_", "-");
        return Stream.of(
                BillingReportLine.builder().user(SHARED_RESOURCE).project(SHARED_RESOURCE).resourceName("SSN").dlabId(ssnId).resourceType(BillingResourceType.SSN).build(),
                BillingReportLine.builder().user(SHARED_RESOURCE).project(SHARED_RESOURCE).resourceName("SSN Volume").dlabId(String.format(VOLUME_PRIMARY_FORMAT, ssnId)).resourceType(BillingResourceType.VOLUME).build(),
                BillingReportLine.builder().user(SHARED_RESOURCE).project(SHARED_RESOURCE).resourceName("SSN bucket").dlabId(bucketName + "-ssn" + "-bucket").resourceType(BillingResourceType.SSN_BUCKET).build(),
                BillingReportLine.builder().user(SHARED_RESOURCE).project(SHARED_RESOURCE).resourceName("Collaboration bucket").dlabId(bucketName + "-shared-bucket").resourceType(BillingResourceType.SHARED_BUCKET).build()
        );
    }

    public static Stream<BillingReportLine> exploratoryBillingDataStream(UserInstanceDTO userInstance) {
        final Stream<BillingReportLine> computationalStream = userInstance.getResources()
                .stream()
                .filter(cr -> cr.getComputationalId() != null)
                .flatMap(cr -> Stream.of(withUserProject(userInstance).dlabId(cr.getComputationalId()).resourceName(cr.getComputationalName()).resourceType(BillingResourceType.COMPUTATIONAL)
                                .status(UserInstanceStatus.of(cr.getStatus())).shape(getComputationalShape(cr)).build(),
                        withUserProject(userInstance).resourceName(cr.getComputationalName() + ":" + VOLUME_PRIMARY).dlabId(String.format(VOLUME_PRIMARY_FORMAT, cr.getComputationalId()))
                                .resourceType(BillingResourceType.VOLUME).build()));
        final String exploratoryId = userInstance.getExploratoryId();
        final String primaryVolumeId = String.format(VOLUME_PRIMARY_FORMAT, exploratoryId);
        final String secondaryVolumeId = String.format(VOLUME_SECONDARY_FORMAT, exploratoryId);
        final Stream<BillingReportLine> exploratoryStream = Stream.of(
                withUserProject(userInstance).resourceName(userInstance.getExploratoryName()).dlabId(exploratoryId).resourceType(BillingResourceType.EXPLORATORY)
                        .status(UserInstanceStatus.of(userInstance.getStatus())).shape(userInstance.getShape()).build(),
                withUserProject(userInstance).resourceName(VOLUME_PRIMARY).dlabId(primaryVolumeId).resourceType(BillingResourceType.VOLUME).build(),
                withUserProject(userInstance).resourceName(VOLUME_SECONDARY).dlabId(secondaryVolumeId).resourceType(BillingResourceType.VOLUME).build());
        return Stream.concat(computationalStream, exploratoryStream);
    }

    private static BillingReportLine.BillingReportLineBuilder withUserProject(UserInstanceDTO userInstance) {
        return BillingReportLine.builder().user(userInstance.getUser()).project(userInstance.getProject());
    }

    public static List<String> getComputationalIds(String computationalId) {
        return Arrays.asList(computationalId, String.format(VOLUME_PRIMARY_FORMAT, computationalId));
    }

    public static List<String> getExploratoryIds(String exploratoryId) {
        return Arrays.asList(exploratoryId, String.format(VOLUME_PRIMARY_FORMAT, exploratoryId), String.format(VOLUME_SECONDARY_FORMAT, exploratoryId));
    }

    private static String getComputationalShape(UserComputationalResource resource) {
        return DataEngineType.fromDockerImageName(resource.getImageName()) == DataEngineType.SPARK_STANDALONE ?
                String.format(DATAENGINE_NAME_FORMAT, resource.getDataengineInstanceCount(), resource.getDataengineShape()) :
                String.format(DATAENGINE_SERVICE_NAME_FORMAT, resource.getMasterNodeShape(), System.lineSeparator(), null, null);
    }

    public static String getFirstLine(String sbn, LocalDate from, LocalDate to) {
        return CSVFormatter.formatLine(Lists.newArrayList(String.format(REPORT_FIRST_LINE, sbn,
                Optional.ofNullable(from).map(date -> date.format(DateTimeFormatter.ISO_DATE)).orElse(StringUtils.EMPTY),
                Optional.ofNullable(to).map(date -> date.format(DateTimeFormatter.ISO_DATE)).orElse(StringUtils.EMPTY))),
                CSVFormatter.SEPARATOR, '\"');
    }

    public static String getHeader() {
        return CSVFormatter.formatLine(new ArrayList<>(Arrays.asList(BillingUtils.REPORT_HEADERS)), CSVFormatter.SEPARATOR);
    }

    public static String printLine(BillingReportLine line) {
        List<String> lines = new ArrayList<>();
        lines.add(getOrEmpty(line.getDlabId()));
        lines.add(getOrEmpty(line.getUser()));
        lines.add(getOrEmpty(line.getProject()));
        lines.add(getOrEmpty(Optional.ofNullable(line.getResourceType()).map(Enum::name).orElse(null)));
        lines.add(getOrEmpty(line.getShape()));
        lines.add(getOrEmpty(line.getProduct()));
        lines.add(getOrEmpty(Optional.ofNullable(line.getCost()).map(String::valueOf).orElse(null)));
        return CSVFormatter.formatLine(lines, CSVFormatter.SEPARATOR);
    }

    public static String getTotal(Double total, String currency) {
        List<String> totalLine = new ArrayList<>();
        for (int i = 0; i < REPORT_HEADERS.length - 1; i++) {
            totalLine.add(StringUtils.EMPTY);
        }
        totalLine.add(REPORT_HEADERS.length - 1, String.format(TOTAL_LINE, getOrEmpty(String.valueOf(total)), getOrEmpty(currency)));
        return CSVFormatter.formatLine(totalLine, CSVFormatter.SEPARATOR);

    }

    private static String getOrEmpty(String s) {
        return Objects.nonNull(s) ? s : StringUtils.EMPTY;
    }
}
