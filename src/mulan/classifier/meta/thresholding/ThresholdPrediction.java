/*
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 *    Meta.java
 *    Copyright (C) 2009 Aristotle University of Thessaloniki, Thessaloniki, Greece
 */
package mulan.classifier.meta.thresholding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import mulan.classifier.MultiLabelLearner;
import mulan.classifier.MultiLabelOutput;
import mulan.data.DataUtils;
import mulan.data.MultiLabelInstances;
import mulan.transformations.RemoveAllLabels;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;

/**
 *
 * @author Marios Ioannou
 * @author George Sakkas
 * @author Grigorios Tsoumakas
 * @version 0.1
 */
public class ThresholdPrediction extends Meta {

    /**
     * Constructor that initializes the learner
     *
     * @param baseLearner the underlying multi-label learner
     * @param classifier the binary classification
     * @param kFolds the number of folds for cross validation
     */
    public ThresholdPrediction(MultiLabelLearner baseLearner, Classifier classifier, String metaDataChoice, int folds) {
        super(baseLearner, classifier, metaDataChoice);
        try {
            foldLearner = baseLearner.makeCopy();
        } catch (Exception ex) {
            Logger.getLogger(ThresholdPrediction.class.getName()).log(Level.SEVERE, null, ex);
        }
        kFoldsCV = folds;
    }

    @Override
    protected MultiLabelOutput makePredictionInternal(Instance instance) throws Exception {
        MultiLabelOutput mlo = baseLearner.makePrediction(instance);
        boolean[] predictedLabels = new boolean[numLabels];
        Instance modifiedIns = modifiedInstanceX(instance, metaDatasetChoice);

        modifiedIns.insertAttributeAt(modifiedIns.numAttributes());
        // set dataset to instance
        modifiedIns.setDataset(classifierInstances);

        double bipartition_key = classifier.classifyInstance(modifiedIns);
        double[] arrayOfScores = new double[numLabels];

        arrayOfScores = mlo.getConfidences();
        for (int i = 0; i < numLabels; i++) {
            if (arrayOfScores[i] >= bipartition_key) {
                predictedLabels[i] = true;
            } else {
                predictedLabels[i] = false;
            }
        }
        MultiLabelOutput final_mlo = new MultiLabelOutput(predictedLabels, mlo.getConfidences());
        return final_mlo;

    }

    public Instances transformData(MultiLabelInstances trainingData) throws Exception {
        // initialize  classifier instances
        classifierInstances = RemoveAllLabels.transformInstances(trainingData);
        classifierInstances = new Instances(classifierInstances, 0);
        classifierInstances.insertAttributeAt(new Attribute("Class"), classifierInstances.numAttributes());
        classifierInstances.setClassIndex(classifierInstances.numAttributes() - 1);

        for (int k = 0; k < kFoldsCV; k++) {
            //Split data to train and test sets
            MultiLabelLearner tempLearner;
            MultiLabelInstances mlTest;
            if (kFoldsCV == 1) {
                tempLearner = baseLearner;
                mlTest = trainingData;
            } else {
                Instances train = trainingData.getDataSet().trainCV(kFoldsCV, k);
                Instances test = trainingData.getDataSet().testCV(kFoldsCV, k);
                MultiLabelInstances mlTrain = new MultiLabelInstances(train, trainingData.getLabelsMetaData());
                mlTest = new MultiLabelInstances(test, trainingData.getLabelsMetaData());
                tempLearner = foldLearner.makeCopy();
                tempLearner.build(mlTrain);
            }

            // copy features and labels, set metalabels
            for (int instanceIndex = 0; instanceIndex < mlTest.getDataSet().numInstances(); instanceIndex++) {
                Instance instance = mlTest.getDataSet().instance(instanceIndex);

                // initialize new class values
                double[] newValues = new double[classifierInstances.numAttributes()];

                // create features
                valuesX(tempLearner, instance, newValues, metaDatasetChoice);

                boolean[] trueLabels = new boolean[numLabels];
                // Indices of labels to take the truelabels for test instances
                for (int i = 0; i < numLabels; i++) {
                    int labelIndice = labelIndices[i];
                    String classValue = mlTest.getDataSet().attribute(labelIndice).value((int) mlTest.getDataSet().instance(instanceIndex).value(labelIndice));
                    trueLabels[i] = classValue.equals("1");
                }

                MultiLabelOutput mlo = baseLearner.makePrediction(mlTest.getDataSet().instance(instanceIndex));
                double[] arrayOfScores = mlo.getConfidences();
                ArrayList<Double> list = new ArrayList();
                for (int i = 0; i < numLabels; i++) {
                    list.add(arrayOfScores[i]);
                }
                Collections.sort(list);
                double tempThresshold = 0, threshold = 0;
                double prev = list.get(0);
                int t = numLabels, tempT = 0;
                for (Double x : list) {
                    tempThresshold = (x + prev) / 2;
                    for (int i = 0; i < numLabels; i++) {
                        if ((trueLabels[i] == true) && (arrayOfScores[i] <= tempThresshold)) {
                            tempT++;
                        } else if ((trueLabels[i] == false) && (arrayOfScores[i] >= tempThresshold)) {
                            tempT++;
                        }
                    }
                    if (tempT < t) {
                        t = tempT;
                        threshold = tempThresshold;
                    }
                    tempT = 0;
                    prev = x;
                }
                newValues[newValues.length - 1] = threshold;

                // add the new insatnce to  classifierInstances
                Instance newInstance = DataUtils.createInstance(mlTest.getDataSet().instance(instanceIndex), mlTest.getDataSet().instance(instanceIndex).weight(), newValues);
                classifierInstances.add(newInstance);
            }
        }
        return classifierInstances;

    }

    @Override
    public TechnicalInformation getTechnicalInformation() {
        TechnicalInformation result = new TechnicalInformation(Type.INPROCEEDINGS);
        result.setValue(Field.AUTHOR, "Elisseeff, Andre and Weston, Jason");
        result.setValue(Field.TITLE, "A kernel method for multi-labelled classification");
        result.setValue(Field.BOOKTITLE, "Proceedings of NIPS 14");
        result.setValue(Field.YEAR, "2002");
        return result;
    }
}
