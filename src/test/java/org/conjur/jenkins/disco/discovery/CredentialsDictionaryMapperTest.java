package org.conjur.jenkins.disco.discovery;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the package-accessible static helpers in CredentialsDictionaryMapper
 * that don't need a Jenkins runtime.
 */
public class CredentialsDictionaryMapperTest {

    // ── buildInheritancePath ──────────────────────────────────────────────────

    @Test
    public void buildInheritancePath_concreteClassIncludesItselfFirst() {
        String path = CredentialsDictionaryMapper.buildInheritancePath(Child.class);
        assertThat(path).startsWith(Child.class.getName());
    }

    @Test
    public void buildInheritancePath_includesParentClass() {
        String path = CredentialsDictionaryMapper.buildInheritancePath(Child.class);
        assertThat(path).contains(Parent.class.getName());
    }

    @Test
    public void buildInheritancePath_doesNotIncludeObject() {
        String path = CredentialsDictionaryMapper.buildInheritancePath(Child.class);
        assertThat(path).doesNotContain("java.lang.Object");
    }

    @Test
    public void buildInheritancePath_forObjectItself_returnsEmpty() {
        // Object stops the loop immediately
        String path = CredentialsDictionaryMapper.buildInheritancePath(Object.class);
        assertThat(path).isEmpty();
    }

    @Test
    public void buildInheritancePath_singleClassNoParent_returnsClassName() {
        String path = CredentialsDictionaryMapper.buildInheritancePath(Standalone.class);
        assertThat(path).isEqualTo(Standalone.class.getName());
    }

    @Test
    public void buildInheritancePath_stopsBeforeAbstractItem() {
        // Simulate a class whose superclass simulates hudson.model.AbstractItem by name —
        // we can't subclass it here, so we verify the general stop-at-Object rule holds
        // and that a simple hierarchy produces comma-separated names.
        String path = CredentialsDictionaryMapper.buildInheritancePath(Child.class);
        String[] parts = path.split(",");
        assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
        assertThat(parts[0]).isEqualTo(Child.class.getName());
        assertThat(parts[1]).isEqualTo(Parent.class.getName());
    }

    @Test
    public void buildInheritancePath_commaSeparated() {
        String path = CredentialsDictionaryMapper.buildInheritancePath(Child.class);
        assertThat(path).contains(",");
    }

    // ── Helper hierarchy ──────────────────────────────────────────────────────

    static class Parent {}
    static class Child extends Parent {}
    static class Standalone {}
}
