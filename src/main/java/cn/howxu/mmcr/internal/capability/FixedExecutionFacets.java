package cn.howxu.mmcr.internal.capability;

/**
 * Internal opt-in for capabilities whose InputRoutingFacet and TickFacet membership
 * is fixed for their lifetime and exactly described by view().facets(). Other facets
 * may remain dynamic; this contract must not be used to cache their availability.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface FixedExecutionFacets {
}
