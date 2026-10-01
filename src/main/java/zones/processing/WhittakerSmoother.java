package zones.processing;

import org.ejml.data.*;
import org.ejml.ops.DConvertMatrixStruct;
import org.ejml.sparse.FillReducing;
import org.ejml.sparse.csc.factory.LinearSolverFactory_DSCC;

/** Solves (W + lambda D2' D2)b = Wy. No ridge, centering, or time rescaling. */
public final class WhittakerSmoother {
    public double[] smooth(double[] signal, boolean[] mask, double lambda) {
        int n=signal.length;
        if(n<3 || mask.length!=n || Statistics.count(mask)<2)
            throw new IllegalArgumentException("Whittaker smoothing requires at least two distinct anchors.");
        double[] diagonal=new double[n], first=new double[n-1], second=new double[n-2];
        for(int i=0;i<n-2;i++) {
            diagonal[i]+=lambda; diagonal[i+1]+=4*lambda; diagonal[i+2]+=lambda;
            first[i]-=2*lambda; first[i+1]-=2*lambda; second[i]+=lambda;
        }
        DMatrixSparseTriplet triplet=new DMatrixSparseTriplet(n,n,5*n);
        DMatrixRMaj rhs=new DMatrixRMaj(n,1), solution=new DMatrixRMaj(n,1);
        for(int i=0;i<n;i++) {
            triplet.addItem(i,i,diagonal[i]+(mask[i]?1:0));
            if(mask[i])rhs.set(i,0,signal[i]);
            if(i<n-1){triplet.addItem(i,i+1,first[i]);triplet.addItem(i+1,i,first[i]);}
            if(i<n-2){triplet.addItem(i,i+2,second[i]);triplet.addItem(i+2,i,second[i]);}
        }
        DMatrixSparseCSC matrix=DConvertMatrixStruct.convert(triplet,(DMatrixSparseCSC)null);
        var solver=LinearSolverFactory_DSCC.cholesky(FillReducing.NONE);
        if(!solver.setA(matrix))throw new IllegalStateException("Whittaker system is singular or not positive definite.");
        solver.solve(rhs,solution);
        double[] result=solution.data.clone();
        for(double value:result)if(!Double.isFinite(value))throw new IllegalStateException("Whittaker smoothing produced a non-finite value.");
        return result;
    }
}
