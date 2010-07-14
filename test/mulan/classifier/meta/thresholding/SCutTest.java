package mulan.classifier.meta.thresholding;

import mulan.classifier.meta.MultiLabelMetaLearnerTest;
import mulan.classifier.transformation.CalibratedLabelRanking;
import mulan.evaluation.measure.HammingLoss;
import weka.classifiers.trees.J48;

public class SCutTest extends MultiLabelMetaLearnerTest {

    @Override
    public void setUp() throws Exception {
        learner = new SCut(new CalibratedLabelRanking(new J48()), new HammingLoss(), 5);
    }
}

