/*******************************************************************************
 * Copyright (c) 2006, 2015 THALES GLOBAL SERVICES.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * 
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *    Thales - initial API and implementation
 *******************************************************************************/
package org.polarsys.capella.transition.system2subsystem.handlers.attachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EObject;
import org.polarsys.capella.common.helpers.EObjectExt;
import org.polarsys.capella.common.helpers.EObjectLabelProviderHelper;
import org.polarsys.capella.core.data.capellacore.InvolvedElement;
import org.polarsys.capella.core.data.cs.BlockArchitecture;
import org.polarsys.capella.core.data.fa.AbstractFunction;
import org.polarsys.capella.core.data.fa.ControlNode;
import org.polarsys.capella.core.data.fa.FaPackage;
import org.polarsys.capella.core.data.fa.FunctionalChain;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvement;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvementFunction;
import org.polarsys.capella.core.data.fa.FunctionalChainInvolvementLink;
import org.polarsys.capella.core.data.fa.FunctionalExchange;
import org.polarsys.capella.core.data.fa.ReferenceHierarchyContext;
import org.polarsys.capella.core.data.fa.SequenceLink;
import org.polarsys.capella.core.data.fa.SequenceLinkEnd;
import org.polarsys.capella.core.model.helpers.BlockArchitectureExt;
import org.polarsys.capella.core.model.helpers.FunctionalChainExt;
import org.polarsys.capella.core.model.helpers.graph.Graph;
import org.polarsys.capella.core.model.helpers.graph.GraphEdge;
import org.polarsys.capella.core.model.helpers.graph.GraphNode;
import org.polarsys.capella.core.transition.common.constants.ITransitionConstants;
import org.polarsys.capella.core.transition.common.handlers.IHandler;
import org.polarsys.capella.core.transition.common.handlers.contextscope.ContextScopeHandlerHelper;
import org.polarsys.capella.core.transition.common.handlers.contextscope.IContextScopeHandler;
import org.polarsys.capella.core.transition.common.handlers.notify.INotifyChangeEvent;
import org.polarsys.capella.core.transition.common.handlers.notify.INotifyListener;
import org.polarsys.capella.core.transition.common.handlers.notify.NotifyHandlerHelper;
import org.polarsys.capella.core.transition.common.handlers.scope.ScopeHandlerHelper;
import org.polarsys.capella.core.transition.common.handlers.transformation.TransformationHandlerHelper;
import org.polarsys.capella.transition.system2subsystem.constants.ISubSystemConstants;
import org.polarsys.capella.transition.system2subsystem.handlers.attachment.FunctionalChainAttachmentHelper.InvolvementGraph.InvolvementEdge;
import org.polarsys.capella.transition.system2subsystem.handlers.attachment.FunctionalChainAttachmentHelper.InvolvementGraph.InvolvementNode;
import org.polarsys.capella.transition.system2subsystem.handlers.scope.ExternalFunctionsScopeRetriever;
import org.polarsys.kitalpha.transposer.rules.handler.rules.api.IContext;

/**
 * 
 */
public class FunctionalChainAttachmentHelper implements IHandler, INotifyListener {

  protected static final String FUNCTIONAL_CHAIN_ATTACHMENT_MAP = "FCAttachmentMap"; //$NON-NLS-1$

  protected static final String MERGE_MAP = "MERGE_MAP"; //$NON-NLS-1$
  protected static final String IS_COMPUTED = "FunctionalChainAttachmentHelper.IS_COMPUTED"; //$NON-NLS-1$

  public class InvolvementGraph
      extends Graph<FunctionalChain, FunctionalChainInvolvement, Integer, InvolvementNode, InvolvementEdge> {

    public class InvolvementEdge extends GraphEdge<Integer, InvolvementNode> {
      public InvolvementEdge(Integer semantic) {
        super(semantic);
      }
    }

    public class InvolvementNode extends GraphNode<FunctionalChainInvolvement, InvolvementEdge> {
      public InvolvementNode(FunctionalChainInvolvement semantic) {
        super(semantic);
      }
    }

    public InvolvementGraph(FunctionalChain chain) {
      super(chain);

      for (FunctionalChainInvolvement inv : FunctionalChainExt.getFlatInvolvements(chain)) {
        getOrCreateNode(inv);
      }

    }

    @Override
    public InvolvementNode createNode(FunctionalChainInvolvement semantic) {
      return new InvolvementNode(semantic);
    }

    @Override
    public InvolvementEdge createEdge(Integer semantic) {
      return new InvolvementEdge(semantic);
    }

  }

  public static FunctionalChainAttachmentHelper getInstance(IContext context_p) {
    FunctionalChainAttachmentHelper handler = (FunctionalChainAttachmentHelper) context_p
        .get(ISubSystemConstants.FUNCTIONAL_CHAIN_ATTACHMENT_HELPER);
    if (handler == null) {
      handler = new FunctionalChainAttachmentHelper();
      handler.init(context_p);
      context_p.put(ISubSystemConstants.FUNCTIONAL_CHAIN_ATTACHMENT_HELPER, handler);
    }
    return handler;
  }

  public Boolean isValidElement(EObject source, IContext context_p) {
    Boolean cache = getValidityMap(context_p).get(source);
    if (cache == null) {
      cache = Boolean.FALSE;
      System.out.println(EObjectLabelProviderHelper.getText(source)+"=cache:"+cache);
    }
    return cache;
  }

  public void setValidElement(EObject source, Boolean target, IContext context_p) {
    getValidityMap(context_p).put(source, target);
    System.out.println(EObjectLabelProviderHelper.getText(source)+"="+target);
  }

  @SuppressWarnings("unchecked")
  protected SubSets<FunctionalChainInvolvementFunction> getMergeSets(IContext context_p) {
    SubSets<FunctionalChainInvolvementFunction> res = (SubSets<FunctionalChainInvolvementFunction>) context_p
        .get(MERGE_MAP);
    if (res == null) {
      res = new SubSets<FunctionalChainInvolvementFunction>();
      context_p.put(MERGE_MAP, res);
    }
    return res;
  }

  protected Map<EObject, Boolean> getValidityMap(IContext context_p) {
    Map<EObject, Boolean> res = (Map<EObject, Boolean>) context_p.get(FUNCTIONAL_CHAIN_ATTACHMENT_MAP);
    if (res == null) {
      res = new HashMap<EObject, Boolean>();
      context_p.put(FUNCTIONAL_CHAIN_ATTACHMENT_MAP, res);
    }
    return res;
  }

  /**
   * {@inheritDoc}
   */
  public IStatus init(IContext context_p) {
    NotifyHandlerHelper.getInstance(context_p).addListener(ITransitionConstants.NOTIFY__END_TRANSFORMATION, this,
        context_p);
    return Status.OK_STATUS;
  }

  /**
   * {@inheritDoc}
   */
  public IStatus dispose(IContext context_p) {
    return Status.OK_STATUS;
  }

  /**
   * Put in the map true for all valid functional chain involvement (stop browsing the chain after the first invalid
   * involvement)
   */
  public void computeChain1(FunctionalChain element_p, IContext context_p) {
    IContextScopeHandler scope = ContextScopeHandlerHelper.getInstance(context_p);

    Collection<FunctionalChainInvolvement> allInvolvements = FunctionalChainExt.getFlatInvolvements(element_p);

    // First, for all Function and FunctionalChains, we define if valid or not.
    for (FunctionalChainInvolvement involvment : allInvolvements) {
      if (getValidityMap(context_p).get(involvment) != null) {
        continue;
      }

      InvolvedElement involvedElement = involvment.getInvolved();
      IStatus willBeTransformed = TransformationHandlerHelper.getInstance(context_p)
          .isOrWillBeTransformed(involvedElement, context_p);

      if (!willBeTransformed.isOK()) {
        setValidElement(involvment, Boolean.FALSE, context_p);

      } else if (involvedElement instanceof AbstractFunction) {
        boolean inScope = ExternalFunctionsScopeRetriever.isPrimaryFunction((AbstractFunction) involvedElement,
            context_p)
            || ExternalFunctionsScopeRetriever.isLinkToPrimaryFunction((AbstractFunction) involvedElement, context_p);
        setValidElement(involvment, inScope, context_p);

      } else if (involvedElement instanceof FunctionalChain) {
        boolean inScope = (!scope.contains(ISubSystemConstants.SCOPE_SECONDARY_ELEMENT, involvedElement, context_p))
            && ScopeHandlerHelper.getInstance(context_p).isInScope(involvedElement, context_p);
        setValidElement(involvment, inScope, context_p);

      } else if (involvedElement instanceof FunctionalExchange) {
        if (involvment.getNextFunctionalChainInvolvements().isEmpty()) {
          setValidElement(involvment, false, context_p);
        }
      }
    }

  }

  public void computeChain2(FunctionalChain element_p, IContext context_p) {
    IContextScopeHandler scope = ContextScopeHandlerHelper.getInstance(context_p);

    Collection<FunctionalChainInvolvement> allInvolvements = FunctionalChainExt.getFlatInvolvements(element_p);

    // Second, we look for all exchanges
    for (FunctionalChainInvolvement involvment : allInvolvements) {
      InvolvedElement involvedElement = involvment.getInvolved();

      if (involvedElement instanceof FunctionalExchange) {
        boolean inScope = (!scope.contains(ISubSystemConstants.SCOPE_SECONDARY_ELEMENT, involvedElement, context_p))
            && ScopeHandlerHelper.getInstance(context_p).isInScope(involvedElement, context_p);

        if (inScope) {
          FunctionalChainInvolvement nextFCI = involvment.getNextFunctionalChainInvolvements().get(0);
          boolean nextIsValid = getValidityMap(context_p).get(nextFCI); // already computed as nextFCI is a function.
          setValidElement(involvment, nextIsValid, context_p);

        } else {
          // A Functional Exchange will be scoped only if its functions are secondary scoped
          boolean prevScoped = false;
          boolean nextScoped = false;

          for (FunctionalChainInvolvement n : involvment.getNextFunctionalChainInvolvements()) {
            if (ExternalFunctionsScopeRetriever.isLinkToPrimaryFunction((AbstractFunction) n.getInvolved(),
                context_p)) {
              nextScoped = true;
              break;
            }
          }

          for (FunctionalChainInvolvement p : involvment.getPreviousFunctionalChainInvolvements()) {
            if (ExternalFunctionsScopeRetriever.isLinkToPrimaryFunction((AbstractFunction) p.getInvolved(),
                context_p)) {
              prevScoped = true;
              break;
            }
          }

          setValidElement(involvment, prevScoped && nextScoped, context_p);
        }
      }
    }

  }

  public Collection<FunctionalChainInvolvement> getNextValid(FunctionalChainInvolvement fci, IContext context_p) {
    LinkedList<FunctionalChainInvolvement> res2 = new LinkedList<>();
    res2.add(fci);
    Collection<LinkedList<FunctionalChainInvolvement>> result = getNextValidInternal(fci, null, context_p, res2);
    for (LinkedList<FunctionalChainInvolvement> i : result) {
      i.removeFirst();
    }
    System.out.println(result);
    return result.iterator().next();
  }

  public Collection<LinkedList<FunctionalChainInvolvement>> getNextValidInternal(FunctionalChainInvolvement fci,
      FunctionalChainInvolvement expected, IContext context_p, LinkedList<FunctionalChainInvolvement> res) {
    Collection<LinkedList<FunctionalChainInvolvement>> result = new ArrayList<LinkedList<FunctionalChainInvolvement>>();

    Collection<FunctionalChainInvolvement> nexts = fci.getNextFunctionalChainInvolvements();
    if (nexts != null) {
      for (FunctionalChainInvolvement next : nexts) {
        if (res.contains(next)) {
          res.clear();
        } else if ((expected == null || next.equals(expected)) && isValidElement(next, context_p)) {
          res.add(next);
          result.add(res);
        } else {
          LinkedList<FunctionalChainInvolvement> res2 = new LinkedList<>();
          res2.addAll(res);
          res2.add(fci);
          result.addAll(getNextValidInternal(next, expected, context_p, res2));
        }
      }
    }

    return result;
  }

  public Collection<FunctionalChainInvolvement> getPreviousValid(FunctionalChainInvolvement fci, IContext context_p) {
    LinkedList<FunctionalChainInvolvement> res2 = new LinkedList<>();
    res2.add(fci);
    Collection<LinkedList<FunctionalChainInvolvement>> result = getPreviousValidInternal(fci, null, context_p, res2);
    for (LinkedList<FunctionalChainInvolvement> i : result) {
      i.removeFirst();
    }
    System.out.println(result);
    return result.iterator().next();
  }

  public Collection<LinkedList<FunctionalChainInvolvement>> getPreviousValidInternal(FunctionalChainInvolvement fci,
      FunctionalChainInvolvement expected, IContext context_p, LinkedList<FunctionalChainInvolvement> res) {
    Collection<LinkedList<FunctionalChainInvolvement>> result = new ArrayList<LinkedList<FunctionalChainInvolvement>>();

    Collection<FunctionalChainInvolvement> nexts = fci.getPreviousFunctionalChainInvolvements();
    if (nexts != null) {
      for (FunctionalChainInvolvement next : nexts) {
        if (res.contains(next)) {
          res.clear();
        } else if ((expected == null || next.equals(expected)) && isValidElement(next, context_p)) {
          res.add(next);
          result.add(res);
        } else {
          LinkedList<FunctionalChainInvolvement> res2 = new LinkedList<>();
          res2.addAll(res);
          res2.add(fci);
          result.addAll(getPreviousValidInternal(next, expected, context_p, res2));
        }
      }
    }

    return result;
  }

  public void merge(FunctionalChainInvolvementFunction tSrc, FunctionalChainInvolvementFunction tTgt,
      IContext context) {
    getMergeSets(context).merge(tSrc, tTgt);
  }

  @Override
  public void notifyChanged(INotifyChangeEvent event, IContext context) {
    for (Collection<FunctionalChainInvolvementFunction> set : getMergeSets(context).getSets()) {
      FunctionalChainInvolvementFunction[] array = set.toArray(new FunctionalChainInvolvementFunction[0]);
      for (int i = 1; i < array.length; i++) {
        for (EObject o : EObjectExt.getReferencers(array[i],
            FaPackage.Literals.FUNCTIONAL_CHAIN_INVOLVEMENT_LINK__SOURCE)) {
          ((FunctionalChainInvolvementLink) o).setSource(array[0]);
        }
        for (EObject o : EObjectExt.getReferencers(array[i],
            FaPackage.Literals.FUNCTIONAL_CHAIN_INVOLVEMENT_LINK__TARGET)) {
          ((FunctionalChainInvolvementLink) o).setTarget(array[0]);
        }
        for (EObject o : EObjectExt.getReferencers(array[i], FaPackage.Literals.SEQUENCE_LINK__SOURCE)) {
          ((SequenceLink) o).setSource(array[0]);
        }
        for (EObject o : EObjectExt.getReferencers(array[i], FaPackage.Literals.SEQUENCE_LINK__TARGET)) {
          ((SequenceLink) o).setTarget(array[0]);
        }
      }
    }
  }

  public void computeChains(FunctionalChain element, IContext context_p) {
    if (context_p.get(IS_COMPUTED) == null) {
      BlockArchitecture architecture = BlockArchitectureExt.getRootBlockArchitecture(element);
      Collection<FunctionalChain> validChainsInScope = FunctionalChainExt.getAllFunctionalChains(architecture).stream()
          .filter(fc -> ScopeHandlerHelper.getInstance(context_p).isInScope(fc, context_p))
          .filter(FunctionalChainExt::isFunctionalChainValid).collect(Collectors.toList());

      for (FunctionalChain chain : validChainsInScope) {
        computeChain1(chain, context_p);
      }
      for (FunctionalChain chain : validChainsInScope) {
        computeChain2(chain, context_p);
      }
      context_p.put(IS_COMPUTED, Boolean.TRUE);
    }
  }
}
