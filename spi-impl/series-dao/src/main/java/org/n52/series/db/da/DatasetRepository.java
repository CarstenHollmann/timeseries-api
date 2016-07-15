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
package org.n52.series.db.da;

import org.n52.series.db.dao.DbQuery;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.joda.time.DateTime;
import org.n52.io.request.IoParameters;
import org.n52.io.response.dataset.measurement.MeasurementSeriesOutput;
import org.n52.io.response.dataset.measurement.MeasurementValue;
import org.n52.io.response.dataset.Data;
import org.n52.io.response.dataset.SeriesParameters;
import org.n52.io.response.dataset.count.CountObservationSeriesOutput;
import org.n52.io.response.dataset.count.CountValue;
import org.n52.io.response.dataset.text.TextObservationSeriesOutput;
import org.n52.io.response.dataset.text.TextValue;
import org.n52.io.response.v1.ext.ObservationType;
import org.n52.io.response.v1.ext.DatasetOutput;
import org.n52.sensorweb.spi.search.SearchResult;
import org.n52.series.db.DataAccessException;
import org.n52.series.db.SessionAwareRepository;
import org.n52.series.db.beans.DescribableEntity;
import org.n52.series.db.beans.DataEntity;
import org.n52.series.db.beans.DatasetEntity;
import org.n52.series.db.beans.CountDataEntity;
import org.n52.series.db.beans.CountDatasetEntity;
import org.n52.series.db.beans.MeasurementDataEntity;
import org.n52.series.db.beans.MeasurementDatasetEntity;
import org.n52.series.db.beans.TextDataEntity;
import org.n52.series.db.beans.TextDatasetEntity;
import org.n52.series.db.dao.ObservationDao;
import org.n52.series.db.dao.SeriesDao;

/**
 * TODO: JavaDoc
 *
 * @author <a href="mailto:h.bredel@52north.org">Henning Bredel</a>
 * @param <T> the dataset's type this repository is responsible for.
 */
public class DatasetRepository<T extends Data>
        extends SessionAwareRepository<DbQuery>
        implements OutputAssembler<DatasetOutput>{

    private DataRepositoryFactory factory = new DataRepositoryFactory();

    @Override
    public boolean exists(String id) throws DataAccessException {
        Session session = getSession();
        try {
            id = ObservationType.extractId(id);
            SeriesDao<DatasetEntity> dao = new SeriesDao<>(session, DatasetEntity.class);
            return dao.hasInstance(parseId(id), DatasetEntity.class);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public List<DatasetOutput> getAllCondensed(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            List<DatasetOutput> results = new ArrayList<>();
            if (query.isSetDatasetTypeFilter()) {
                for (String datasetType : query.getDatasetTypes()) {
                    addResults(getSeriesDao(datasetType, session), query, results);
                }
            } else {
                addResults(getSeriesDao(DatasetEntity.class, session), query, results);
            }
            return results;
        } finally {
            returnSession(session);
        }
    }

    private void addResults(SeriesDao<? extends DatasetEntity> dao, DbQuery query, List<DatasetOutput> results) throws DataAccessException {
        for (DatasetEntity series : dao.getAllInstances(query)) {
            results.add(createCondensed(series, query));
        }
    }

    private SeriesDao<? extends DatasetEntity> getSeriesDao(String datasetType, Session session) {
        final DataRepository dataRepository = factory.createRepository(datasetType);
        return getSeriesDao(dataRepository.getEntityType(), session);
    }

    private SeriesDao<? extends DatasetEntity> getSeriesDao(Class<? extends DatasetEntity> clazz, Session session) {
        return new SeriesDao<>(session, clazz);
    }

    @Override
    public List<DatasetOutput> getAllExpanded(DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            List<DatasetOutput> results = new ArrayList<>();
            for (String datasetType : query.getDatasetTypes()) {
                SeriesDao<? extends DatasetEntity> dao = getSeriesDao(datasetType, session);
                for (DatasetEntity series : dao.getAllInstances(query)) {
                    results.add(createExpanded(series, query, session));
                }
            }
            return results;
        } finally {
            returnSession(session);
        }
    }

    @Override
    public DatasetOutput getInstance(String id, DbQuery query) throws DataAccessException {
        Session session = getSession();
        try {
            String seriesId = ObservationType.extractId(id);
            final String datasetType = ObservationType.extractType(id).getObservationType();
            SeriesDao<? extends DatasetEntity> dao = getSeriesDao(datasetType, session);
            DatasetEntity instance = dao.getInstance(Long.parseLong(seriesId), query);
            return createExpanded(instance, query, session);
        } finally {
            returnSession(session);
        }
    }

    @Override
    public Collection<SearchResult> searchFor(IoParameters paramters) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<SearchResult> convertToSearchResults(List<? extends DescribableEntity> found, String locale) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private DatasetOutput createCondensed(DatasetEntity<?> series, DbQuery query) throws DataAccessException {
        if (series instanceof MeasurementDatasetEntity) {
            MeasurementSeriesOutput output = new MeasurementSeriesOutput();
            output.setLabel(createSeriesLabel(series, query.getLocale()));
            output.setId(series.getPkid().toString());
            output.setHrefBase(urHelper.getSeriesHrefBaseUrl(query.getHrefBase()));
            return output;
        } else if (series instanceof TextDatasetEntity) {
            TextObservationSeriesOutput output = new TextObservationSeriesOutput();
            output.setLabel(createSeriesLabel(series, query.getLocale()));
            output.setId(series.getPkid().toString());
            output.setHrefBase(urHelper.getSeriesHrefBaseUrl(query.getHrefBase()));
            return output;
        } else if (series instanceof CountDatasetEntity) {
            CountObservationSeriesOutput output = new CountObservationSeriesOutput();
            output.setLabel(createSeriesLabel(series, query.getLocale()));
            output.setId(series.getPkid().toString());
            output.setHrefBase(urHelper.getSeriesHrefBaseUrl(query.getHrefBase()));
            return output;
        }
        return null;
    }

    private DatasetOutput createExpanded(DatasetEntity<?> series, DbQuery query, Session session) throws DataAccessException {
        DatasetOutput result = createCondensed(series, query);
        result.setSeriesParameters(getParameters(series, query));
        if (series instanceof MeasurementDatasetEntity && result instanceof MeasurementSeriesOutput) {
            MeasurementDatasetEntity measurementSeries = (MeasurementDatasetEntity) series;
            MeasurementSeriesOutput output = (MeasurementSeriesOutput) result;
            output.setUom(measurementSeries.getUnitI18nName(query.getLocale()));
            output.setFirstValue(createSeriesValueFor(measurementSeries.getFirstValue(), measurementSeries));
            output.setLastValue(createSeriesValueFor(measurementSeries.getLastValue(), measurementSeries));
        } else if (series instanceof TextDatasetEntity && result instanceof TextObservationSeriesOutput) {
            TextDatasetEntity textObservationSeries = (TextDatasetEntity) series;
            TextObservationSeriesOutput output = (TextObservationSeriesOutput) result;
            output.setFirstValue(createSeriesValueFor(textObservationSeries.getFirstValue(), textObservationSeries));
            output.setLastValue(createSeriesValueFor(textObservationSeries.getLastValue(), textObservationSeries));
        } else if (series instanceof CountDatasetEntity && result instanceof CountObservationSeriesOutput) {
            CountDatasetEntity countObservationSeries = (CountDatasetEntity) series;
            CountObservationSeriesOutput output = (CountObservationSeriesOutput) result;
            output.setFirstValue(createSeriesValueFor(countObservationSeries.getFirstValue(), countObservationSeries));
            output.setLastValue(createSeriesValueFor(countObservationSeries.getLastValue(), countObservationSeries));
        }
        return result;
    }

    private SeriesParameters getParameters(DatasetEntity<?> series, DbQuery query) throws DataAccessException {
        return createSeriesParameters(series, query);
    }

    private String createSeriesLabel(DatasetEntity<?> series, String locale) {
        String station = getLabelFrom(series.getFeature(), locale);
        String procedure = getLabelFrom(series.getProcedure(), locale);
        String phenomenon = getLabelFrom(series.getPhenomenon(), locale);
        StringBuilder sb = new StringBuilder();
        sb.append(phenomenon).append(" ");
        sb.append(procedure).append(", ");
        return sb.append(station).toString();
    }

    private MeasurementValue createSeriesValueFor(MeasurementDataEntity observation, MeasurementDatasetEntity series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        }
        MeasurementValue value = new MeasurementValue();
        value.setTimestamp(observation.getTimestamp().getTime());
        Double observationValue = !getServiceInfo().isNoDataValue(observation)
                ? formatDecimal(observation.getValue(), series)
                : Double.NaN;
        value.setValue(observationValue);
        return value;
    }

    private TextValue createSeriesValueFor(TextDataEntity observation,
            TextDatasetEntity series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        } else if (observation.getValue() == null) {
            return (TextValue)queryObservationFor(observation, series, null);
        }
        TextValue value = new TextValue();
        value.setTimestamp(observation.getTimestamp().getTime());
        value.setValue(observation.getValue());
        return value;
    }

    private CountValue createSeriesValueFor(CountDataEntity observation,
            CountDatasetEntity series) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        } else if (observation.getValue() == null) {
            return (CountValue)queryObservationFor(observation, series, null);
        }
        CountValue value = new CountValue();
        value.setTimestamp(observation.getTimestamp().getTime());
        value.setValue(observation.getValue());
        return value;
    }

    private Data queryObservationFor(DataEntity observation, DatasetEntity<?> series, DbQuery query) {
        if (observation == null) {
            // do not fail on empty observations
            return null;
        }
        if (query == null) {
            query = DbQuery.createFrom(IoParameters.createDefaults());
        }
        DateTime timestamp = new DateTime(observation.getTimestamp());
        List<DataEntity> observations = new ObservationDao(getSession()).getInstancesFor(timestamp, series, query);
        if (observations != null && !observations.isEmpty()) {
            if (series instanceof MeasurementDatasetEntity) {
                return createSeriesValueFor((MeasurementDataEntity)observations.iterator().next(), (MeasurementDatasetEntity)series);
            } else if (series instanceof TextDatasetEntity) {
                return createSeriesValueFor((TextDataEntity)observations.iterator().next(), (TextDatasetEntity)series);
            } else if (series instanceof CountDatasetEntity) {
                return createSeriesValueFor((CountDataEntity)observations.iterator().next(), (CountDatasetEntity)series);
            }
        }
        return null;
    }

    private Double formatDecimal(Double value, MeasurementDatasetEntity series) {
        int scale = series.getNumberOfDecimals();
        return new BigDecimal(value)
                .setScale(scale, HALF_UP)
                .doubleValue();
    }

    public DataRepositoryFactory getDataRepositoryFactory() {
        return factory;
    }

    public void setDataRepositoryFactory(DataRepositoryFactory factory) {
        this.factory = factory;
    }

}
