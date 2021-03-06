/*******************************************************************************
 * Copyright 2016 Intuit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.intuit.wasabi.experiment.impl;

import com.intuit.wasabi.authenticationobjects.UserInfo;
import com.intuit.wasabi.eventlog.EventLog;
import com.intuit.wasabi.eventlog.events.ExperimentChangeEvent;
import com.intuit.wasabi.exceptions.EndTimeHasPassedException;
import com.intuit.wasabi.exceptions.ExperimentNotFoundException;
import com.intuit.wasabi.experiment.Experiments;
import com.intuit.wasabi.experiment.Mutex;
import com.intuit.wasabi.experimentobjects.Experiment;
import com.intuit.wasabi.experimentobjects.ExperimentIDList;
import com.intuit.wasabi.experimentobjects.ExperimentList;
import com.intuit.wasabi.experimentobjects.exceptions.InvalidExperimentStateException;
import com.intuit.wasabi.repository.MutexRepository;
import com.intuit.wasabi.repository.RepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.*;

import static com.intuit.wasabi.experimentobjects.Experiment.State.DELETED;
import static com.intuit.wasabi.experimentobjects.Experiment.State.TERMINATED;

public class MutexImpl implements Mutex {

    private final MutexRepository mutexRepository;
    private final Experiments experiments;
    private final EventLog eventLog;
    private static final Logger LOGGER = LoggerFactory.getLogger(MutexImpl.class);

    final Date NOW = new Date();

    @Inject
    public MutexImpl(MutexRepository mutexRepository, Experiments experiments, EventLog eventLog) {
        super();
        this.mutexRepository = mutexRepository;
        this.experiments = experiments;
        this.eventLog = eventLog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExperimentList getExclusions(Experiment.ID experimentID) {

        // Throw an exception if the input experiment is not valid
        final Experiment expID = experiments.getExperiment(experimentID);
        if (expID.getID() == null) {
            throw new ExperimentNotFoundException(experimentID);
        }
        return mutexRepository.getExclusions(experimentID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExperimentList getNotExclusions(Experiment.ID experimentID) {
        // Throw an exception if the input experiment is not valid
        if (experimentID == null) {
            throw new ExperimentNotFoundException("error, experiment not found");
        }
        return mutexRepository.getNotExclusions(experimentID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteExclusion(Experiment.ID expID_1, Experiment.ID expID_2, UserInfo user) {
        Experiment exp_1 = experiments.getExperiment(expID_1);
        Experiment exp_2 = experiments.getExperiment(expID_2);

        // Check that expID_1 is a valid experiment
        if (exp_1 == null) {
            throw new ExperimentNotFoundException(expID_1);
        }

        // Check that expID_2 is a valid experiment
        if (exp_2 == null) {
            throw new ExperimentNotFoundException(expID_2);
        }

        mutexRepository.deleteExclusion(expID_1, expID_2);
        eventLog.postEvent(new ExperimentChangeEvent(user, exp_1, "mutex", exp_2.getLabel().toString(), null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Map> createExclusions(Experiment.ID baseID, ExperimentIDList experimentIDList, UserInfo user) {

        // Get the base experiment
        Experiment baseExp = experiments.getExperiment(baseID);

        // Throw an exception if the base experiment is not valid
        if (baseExp == null) {
            throw new ExperimentNotFoundException(baseID);
        }

        // Get the current state of the base experiment
        Experiment.State baseCurrentState = baseExp.getState();

        // Throw an exception if base experiment is in an invalid state
        if (baseCurrentState.equals(TERMINATED) || baseCurrentState.equals(DELETED)) {
            throw new InvalidExperimentStateException(new StringBuilder("Invalid experiment state").append(baseID)
                    .append("\"").append("Can not define mutual exclusion rules when an experiment is in")
                    .append(baseCurrentState).append("\"").append("state").toString());
        }

        // Get the timestamp for current time
        final Date nowTimestamp = new Timestamp(NOW.getTime());

        // Get the end time of the base experiment
        Date baseEndTime = baseExp.getEndTime();
        final Date baseTimestamp = new Timestamp(baseEndTime.getTime());

        // Throw an exception if the base end time has passed
        if (nowTimestamp.after(baseTimestamp)) {
            throw new EndTimeHasPassedException(baseID, baseEndTime);
        }

        List<Map> results = new ArrayList<>();

        // Loop through the ExperimentBatch list
        for (Experiment.ID pairID : experimentIDList.getExperimentIDs()) {

            Map<String, Object> tempResult = new HashMap<>();
            tempResult.put("experimentID1", baseID);
            tempResult.put("experimentID2", pairID);

            // Check to see if pair experiment exists
            Experiment pairExp = experiments.getExperiment(pairID);

            if (pairExp == null) {
                tempResult.put("status", "FAILED");
                tempResult.put("reason", "Experiment2 not found.");
                results.add(tempResult);
                continue;
            }

            // Check to see if base and pair experiments are in the same application
            if (!pairExp.getApplicationName().equals(baseExp.getApplicationName())) {
                tempResult.put("status", "FAILED");
                tempResult.put("reason", new StringBuilder("Experiments 1 and 2 are not in the same application. ")
                        .append("Mutual exclusion rules can only be defined for experiments within the same application.").toString());
                results.add(tempResult);
                continue;
            }

            // Get the current state of the pair experiment
            Experiment.State pairCurrentState = pairExp.getState();
            // Throw an exception if the pair experiment is in an invalid state
            if (pairCurrentState.equals(TERMINATED) || pairCurrentState.equals(DELETED)) {
                tempResult.put("status", "FAILED");
                tempResult.put("reason", "Experiment2 is in TERMINATED or DELETED state");
                results.add(tempResult);
                continue;
            }

            // Get the end time of the pair experiment
            Date pairEndTime = pairExp.getEndTime();
            final Date pairTimestamp = new Timestamp(pairEndTime.getTime());

            // Throw an exception if the base end time has passed
            if (nowTimestamp.after(pairTimestamp)) {
                tempResult.put("status", "FAILED");
                tempResult.put("reason", "End time has passed for experiment2");
                results.add(tempResult);
                continue;
            }

            //add the pair
            try {
                mutexRepository.createExclusion(baseID, pairID);
                eventLog.postEvent(new ExperimentChangeEvent(user, baseExp, "mutex",
                        null, pairExp.getLabel() == null ? null : pairExp.getLabel().toString()));
            } catch (RepositoryException rExp) {
                LOGGER.error("Unable to store data in repository: ", rExp);
                tempResult.put("status", "FAILED");
                tempResult.put("reason", "Repository exception");
                results.add(tempResult);
                continue;
            }
            tempResult.put("status", "SUCCESS");
            results.add(tempResult);
        }
        return results;
    }
}
