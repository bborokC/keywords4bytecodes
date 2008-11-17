package mulan.classifier;

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

import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.TechnicalInformation;
import weka.core.TechnicalInformation.Field;
import weka.core.TechnicalInformation.Type;


/**
 * Base class for multi-label classifiers, 
 * which use problem transformation to handle multi-label data.
 *
 * @author Robert Friberg
 * @author Jozef Vilcek
 * @version $Revision: 0.02 $ 
*/
public abstract class TransformationBasedMultiLabelClassifier extends MultiLabelClassifierBase {

	
	/**
	 * The encapsulated classifier or used for making clones in the 
	 * case of ensemble classifiers. 
	 */
	protected final Classifier baseClassifier;
	
	/**
	 * Creates a new instance
	 *  
	 * @param numLabels the number of labels for the classifier.
	 */
	public TransformationBasedMultiLabelClassifier(int numLabels)
	{
		this(new J48(), numLabels);
	}
	
	/**
	 * Creates a new instance.
	 * 
	 * @param baseClassifier the base classifier which will be used internally to classify the data.
	 * @param numLabels the number of labels for the classifier.
	 */
	public TransformationBasedMultiLabelClassifier(Classifier baseClassifier, int numLabels)
	{
		super(numLabels);
		this.baseClassifier = baseClassifier;
	}
	
	/**
	 * Returns the {@link Classifier} which is used internally by this classifier.
	 *  
	 * @return
	 */
	public Classifier getBaseClassifier()
	{
		return baseClassifier;
	}

	public TechnicalInformation getTechnicalInformation()
	{
	    TechnicalInformation result = new TechnicalInformation(Type.ARTICLE);
	    result.setValue(Field.AUTHOR, "Grigorios Tsoumakas, Ioannis Katakis");
        result.setValue(Field.YEAR, "2007");
        result.setValue(Field.TITLE, "Multi-Label Classification: An Overview");
        result.setValue(Field.JOURNAL, "International Journal of Data Warehousing and Mining");
        result.setValue(Field.VOLUME, "3(3)");
        result.setValue(Field.PAGES, "1-13");
	    return result;
	}
}
