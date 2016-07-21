/*
 * Copyright (C) 2013-2016 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public License
 * version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */

package org.n52.web.ctrl;

import static org.n52.io.MimeType.APPLICATION_ZIP;
import static org.n52.io.MimeType.TEXT_CSV;
import static org.n52.io.request.IoParameters.createFromQuery;
import static org.n52.io.request.QueryParameters.createFromQuery;
import static org.n52.io.request.RequestSimpleParameterSet.createForSingleSeries;
import static org.n52.io.request.RequestSimpleParameterSet.createFromDesignedParameters;
import static org.n52.web.ctrl.UrlSettings.COLLECTION_DATASETS;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.n52.io.DatasetFactoryException;
import org.n52.io.DefaultIoFactory;
import org.n52.io.IntervalWithTimeZone;
import org.n52.io.IoFactory;
import org.n52.io.IoHandler;
import org.n52.io.IoProcessChain;
import org.n52.io.PreRenderingJob;
import org.n52.io.request.IoParameters;
import org.n52.io.request.RequestSimpleParameterSet;
import org.n52.io.request.RequestStyledParameterSet;
import org.n52.io.response.dataset.Data;
import org.n52.io.response.dataset.DataCollection;
import org.n52.io.response.dataset.measurement.MeasurementData;
import org.n52.io.response.v1.ext.DatasetOutput;
import org.n52.io.response.v1.ext.DatasetType;
import org.n52.io.v1.data.RawFormats;
import org.n52.series.spi.srv.DataService;
import org.n52.series.spi.srv.ParameterService;
import org.n52.series.spi.srv.RawDataService;
import org.n52.web.exception.BadRequestException;
import org.n52.web.exception.InternalServerException;
import org.n52.web.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequestMapping(value = COLLECTION_DATASETS, produces = {"application/json"})
public class DataController extends BaseController {

    private final static Logger LOGGER = LoggerFactory.getLogger(DataController.class);

    private DataService<? extends Data> dataService;

    private ParameterService<? extends DatasetOutput> datasetService;

    private PreRenderingJob preRenderingTask;

    private String requestIntervalRestriction;

    @RequestMapping(value = "/data", produces = {"application/json"}, method = POST)
    public ModelAndView getSeriesCollectionData(HttpServletResponse response,
                                                @RequestBody RequestSimpleParameterSet parameters) throws Exception {

        checkForUnknownSeriesIds(parameters.getSeriesIds());
        checkAgainstTimespanRestriction(parameters.getTimespan());

        final String datasetType = parameters.getDatasetTypeFromFirst();
        IoProcessChain ioChain = createIoFactory(datasetType)
                .withSimpleRequest(parameters)
                .createProcessChain();

        DataCollection<?> processed = ioChain.getData();
        return new ModelAndView().addObject(processed.getAllSeries());
    }

    @RequestMapping(value = "/{seriesId}/data", produces = {"application/json"}, method = GET)
    public ModelAndView getSeriesData(HttpServletResponse response,
                                      @PathVariable String seriesId,
                                      @RequestParam(required = false) MultiValueMap<String, String> query) throws Exception {

        IoParameters map = createFromQuery(query);
        IntervalWithTimeZone timespan = map.getTimespan();
        checkAgainstTimespanRestriction(timespan.toString());
        checkForUnknownSeriesIds(seriesId);

        RequestSimpleParameterSet parameters = createForSingleSeries(seriesId, map);
        String datasetType = DatasetType.extractType(seriesId);
        IoProcessChain ioChain = createIoFactory(datasetType)
                .withSimpleRequest(parameters)
                .createProcessChain();

        DataCollection<? extends Data> formattedDataCollection = ioChain.getProcessedData();
        final Map<String, ?> processed = formattedDataCollection.getAllSeries();
        return map.isExpanded()
                ? new ModelAndView().addObject(processed)
                : new ModelAndView().addObject(processed.get(seriesId));
    }

    @RequestMapping(value = "/data", method = POST, params = {RawFormats.RAW_FORMAT})
    public void getRawSeriesCollectionData(HttpServletResponse response,
                                           @RequestBody RequestSimpleParameterSet parameters) throws Exception {
        checkForUnknownSeriesIds(parameters.getSeriesIds());
        writeRawData(parameters, response);
    }

    @RequestMapping(value = "/{seriesId}/data", method = GET, params = {RawFormats.RAW_FORMAT})
    public void getRawSeriesData(HttpServletResponse response,
                                 @PathVariable String seriesId,
                                 @RequestParam MultiValueMap<String, String> query) {
        checkForUnknownSeriesIds(seriesId);
        IoParameters map = createFromQuery(query);
        RequestSimpleParameterSet parameters = createForSingleSeries(seriesId, map);
        writeRawData(parameters, response);
    }

    private void writeRawData(RequestSimpleParameterSet parameters, HttpServletResponse response) throws InternalServerException, ResourceNotFoundException, BadRequestException {
        if (!dataService.supportsRawData()) {
            throw new BadRequestException("Querying of raw timeseries data is not supported by the underlying service!");
        }
        final RawDataService rawDataService = dataService.getRawDataService();
        try (final InputStream inputStream = rawDataService.getRawData(parameters)) {
            if (inputStream == null) {
                throw new ResourceNotFoundException("No raw data found.");
            }
            IOUtils.copyLarge(inputStream, response.getOutputStream());
        } catch (IOException e) {
            throw new InternalServerException("Error while querying raw data", e);
        }
    }

    @RequestMapping(value = "/data", produces = {"application/pdf"}, method = POST)
    public void getSeriesCollectionReport(HttpServletResponse response,
                                          @RequestBody RequestStyledParameterSet requestParameters) throws Exception {

        IoParameters map = createFromQuery(requestParameters);
//        parameters.setGeneralize(map.isGeneralize());
//        parameters.setExpanded(map.isExpanded());

        checkForUnknownSeriesIds(requestParameters.getSeriesIds());
        checkAgainstTimespanRestriction(requestParameters.getTimespan());

        final String datasetType = requestParameters.getDatasetTypeFromFirst();
        IoHandler<? extends Data> ioHandler = createIoFactory(datasetType)
                .withSimpleRequest(createFromDesignedParameters(requestParameters))
                .withStyledRequest(requestParameters)
                .createHandler("application/pdf");
        ioHandler.writeBinary(response.getOutputStream());

    }

    @RequestMapping(value = "/{seriesId}/data", produces = {"application/pdf"}, method = GET)
    public void getSeriesReport(HttpServletResponse response,
                                @PathVariable String seriesId,
                                @RequestParam(required = false) MultiValueMap<String, String> query) throws Exception {

        IoParameters map = createFromQuery(query);
        RequestSimpleParameterSet parameters = createForSingleSeries(seriesId, map);

        checkAgainstTimespanRestriction(parameters.getTimespan());
        checkForUnknownSeriesIds(seriesId);

//        parameters.setGeneralize(map.isGeneralize());
//        parameters.setExpanded(map.isExpanded());

        final String datasetType = parameters.getDatasetTypeFromFirst();
        IoHandler<? extends Data> ioHandler = createIoFactory(datasetType)
                .withSimpleRequest(parameters)
                .createHandler("application/pdf");
        ioHandler.writeBinary(response.getOutputStream());
    }

    @RequestMapping(value = "/{seriesId}/data", produces = {"application/zip"}, method = GET)
    public void getSeriesAsZippedCsv(HttpServletResponse response,
                                     @PathVariable String seriesId,
                                     @RequestParam(required = false) MultiValueMap<String, String> query)
                                             throws Exception {
        query.put("zip", Arrays.asList(new String[] {Boolean.TRUE.toString()}));
        getSeriesAsCsv(response, seriesId, query);
    }

    @RequestMapping(value = "/{seriesId}/data", produces = {"text/csv"}, method = GET)
    public void getSeriesAsCsv(HttpServletResponse response,
                              @PathVariable String seriesId,
                              @RequestParam(required = false) MultiValueMap<String, String> query) throws Exception {


        IoParameters map = createFromQuery(query);
        RequestSimpleParameterSet parameters = createForSingleSeries(seriesId, map);

        checkAgainstTimespanRestriction(parameters.getTimespan());
        checkForUnknownSeriesIds(seriesId);

//        parameters.setGeneralize(map.isGeneralize());
//        parameters.setExpanded(map.isExpanded());

        response.setCharacterEncoding("UTF-8");
        if (Boolean.parseBoolean(map.getOther("zip"))) {
            response.setContentType(APPLICATION_ZIP.toString());
        }
        else {
            response.setContentType(TEXT_CSV.toString());
        }

        final String datasetType = parameters.getDatasetTypeFromFirst();
        IoHandler<? extends Data> ioHandler = createIoFactory(datasetType)
                .withSimpleRequest(parameters)
                .createHandler("text/csv");
        ioHandler.writeBinary(response.getOutputStream());
    }

    @RequestMapping(value = "/data", produces = {"image/png"}, method = POST)
    public void getSeriesCollectionChart(HttpServletResponse response,
                                         @RequestBody RequestStyledParameterSet requestParameters) throws Exception {

        checkForUnknownSeriesIds(requestParameters.getSeriesIds());

//        IoParameters map = createFromQuery(requestParameters);
//        checkAgainstTimespanRestriction(requestParameters.getTimespan());
//        requestParameters.setGeneralize(map.isGeneralize());
//        requestParameters.setExpanded(map.isExpanded());
//        requestParameters.setBase64(map.isBase64());

        final String datasetType = requestParameters.getDatasetTypeFromFirst();
        IoHandler<? extends Data> ioHandler = createIoFactory(datasetType)
                .withStyledRequest(requestParameters)
                .createHandler("image/png");
        ioHandler.writeBinary(response.getOutputStream());
    }

    @RequestMapping(value = "/{seriesId}/data", produces = {"image/png"}, method = GET)
    public void getSeriesChart(HttpServletResponse response,
                               @PathVariable String seriesId,
                               @RequestParam(required = false) MultiValueMap<String, String> query) throws Exception {

        IoParameters map = createFromQuery(query);
        checkAgainstTimespanRestriction(map.getTimespan().toString());
        checkForUnknownSeriesIds(seriesId);

        String observationType = DatasetType.extractType(seriesId);
        RequestSimpleParameterSet parameters = map.toSimpleParameterSet();
        IoHandler<? extends Data> ioHandler = createIoFactory(observationType)
                .withSimpleRequest(parameters)
                .createHandler("image/png");
        ioHandler.writeBinary(response.getOutputStream());
    }

    @RequestMapping(value = "/{seriesId}/{chartQualifier}", produces = {"image/png"}, method = GET)
    public void getSeriesChartByInterval(HttpServletResponse response,
                                         @PathVariable String seriesId,
                                         @PathVariable String chartQualifier,
                                         @RequestParam(required = false) MultiValueMap<String, String> query)
                                                 throws Exception {
        if (preRenderingTask == null) {
            throw new ResourceNotFoundException("Diagram prerendering is not enabled.");
        }
        if ( !preRenderingTask.hasPrerenderedImage(seriesId, chartQualifier)) {
            throw new ResourceNotFoundException("No pre-rendered chart found for timeseries '" + seriesId + "'.");
        }
        preRenderingTask.writePrerenderedGraphToOutputStream(seriesId, chartQualifier, response.getOutputStream());
    }

    private void checkAgainstTimespanRestriction(String timespan) {
        Duration duration = Period.parse(requestIntervalRestriction).toDurationFrom(new DateTime());
        if (duration.getMillis() < Interval.parse(timespan).toDurationMillis()) {
            throw new BadRequestException("Requested timespan is to long, please use a period shorter than '"
                    + requestIntervalRestriction + "'");
        }
    }

    private void checkForUnknownSeriesIds(String... seriesIds) {
        for (String id : seriesIds) {
            if ( !datasetService.exists(id)) {
                throw new ResourceNotFoundException("The series with id '" + id + "' was not found.");
            }
        }
    }

    private IoFactory<MeasurementData> createIoFactory(final String datasetType) throws DatasetFactoryException {
        return new DefaultIoFactory()
                .create(datasetType)
//                .withBasePath(getRootResource())
                .withDataService(dataService)
                .withDatasetService(datasetService);
    }

    private URI getRootResource() throws URISyntaxException, MalformedURLException {
        return getServletConfig().getServletContext().getResource("/").toURI();
    }

    // TODO set preredering config instead of task

    public PreRenderingJob getPreRenderingTask() {
        return preRenderingTask;
    }

    public void setPreRenderingTask(PreRenderingJob prerenderingTask) {
        this.preRenderingTask = prerenderingTask;
    }

    public String getRequestIntervalRestriction() {
        return requestIntervalRestriction;
    }

    public void setRequestIntervalRestriction(String requestIntervalRestriction) {
        // validate requestIntervalRestriction, if it's no period an exception occured
        Period.parse(requestIntervalRestriction);
        this.requestIntervalRestriction = requestIntervalRestriction;
    }

    public DataService<? extends Data> getDataService() {
        return dataService;
    }

    public void setDataService(DataService<? extends Data> dataService) {
        this.dataService = dataService;
    }

    public ParameterService<? extends DatasetOutput> getDatasetService() {
        return datasetService;
    }

    public void setDatasetService(ParameterService<? extends DatasetOutput> datasetService) {
        this.datasetService = datasetService;
    }


}
