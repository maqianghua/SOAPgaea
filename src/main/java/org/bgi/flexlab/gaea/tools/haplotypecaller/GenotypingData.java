package org.bgi.flexlab.gaea.tools.haplotypecaller;

import org.bgi.flexlab.gaea.tools.haplotypecaller.allele.AlleleList;
import org.bgi.flexlab.gaea.util.Utils;

import htsjdk.variant.variantcontext.Allele;

public final class GenotypingData<A extends Allele> implements SampleList, AlleleList<A> {

    private final PloidyModel ploidyModel;

    private final ReadLikelihoods<A> likelihoods;

    /**
     * Constructs a new genotyping-data collection providing the ploidy model to apply to the input model
     * and the read-likelihoods collection.
     *
     *
     * @param ploidyModel the ploidy model.
     * @param likelihoods the read-likelihoods collection.
     *
     * @throws IllegalArgumentException if either {@code ploidyModel} or {@code likelihoods} is {@code null},
     *   or they are not compatible in terms of the samples they contain; their lists must match.
     */
    public GenotypingData(final PloidyModel ploidyModel, final ReadLikelihoods<A> likelihoods) {
        Utils.nonNull(ploidyModel, "the ploidy model cannot be null");
        Utils.nonNull(likelihoods, "the likelihood object cannot be null");
        this.ploidyModel = ploidyModel;
        this.likelihoods = likelihoods;
        Utils.validateArg(ploidyModel.asListOfSamples().equals(likelihoods.asListOfSamples()),
                "sample list are different between ploidy-model and read-likelihood collection, perhaps just the order");
    }

    /**
     * Returns the ploidy model that corresponds to the data provided.
     * @return never {@code null}.
     */
    public PloidyModel ploidyModel() {
        return ploidyModel;
    }

    @Override
    public int numberOfSamples() {
        return ploidyModel.numberOfSamples();
    }

    @Override
    public int indexOfSample(final String sample) {
        Utils.nonNull(sample);
        return ploidyModel.indexOfSample(sample);
    }

    @Override
    public String getSample(final int sampleIndex) {
        Utils.validateArg(sampleIndex >= 0, "sampleIndex");
        return ploidyModel.getSample(sampleIndex);
    }

    /**
     * Returns read-likelihoods to use for genotyping.
     * @return never {@code null}.
     */
    public ReadLikelihoods<A> readLikelihoods() {
        return likelihoods;
    }

    @Override
    public int numberOfAlleles() {
        return likelihoods.numberOfAlleles();
    }

    @Override
    public int indexOfAllele(final A allele) {
        Utils.nonNull(allele);
        return likelihoods.indexOfAllele(allele);
    }

    @Override
    public A getAllele(final int index) {
        Utils.validateArg(index >= 0, "index");
        return likelihoods.getAllele(index);
    }
}

