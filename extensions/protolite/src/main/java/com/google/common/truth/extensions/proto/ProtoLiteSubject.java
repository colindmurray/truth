/*
 * Copyright (c) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.truth.extensions.proto;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.base.Objects;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.TestVerb;
import com.google.common.truth.Truth;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Truth subjects for the Lite version of Protocol Buffers.
 *
 * <p>ProtoLiteSubject supports versions 2 and 3 of Protocol Buffers. Due to the lack of runtime
 * descriptors, its functionality is limited compared to ProtoSubject, in particular in performing
 * detailed comparisons between messages.
 *
 * @param <S> Subject class type.
 * @param <M> MessageLiteOrBuilder type.
 */
public class ProtoLiteSubject<S extends ProtoLiteSubject<S, M>, M extends MessageLiteOrBuilder>
    extends Subject<S, M> {

  /**
   * Typed extension of {@link SubjectFactory}.
   *
   * <p>The existence of this class is necessary in order to satisfy the generic constraints of
   * {@link Truth#assertAbout(SubjectFactory)}, whilst also hiding the Untyped classes which are not
   * meant to be exposed.
   */
  public abstract static class Factory<
          S extends ProtoLiteSubject<S, M>, M extends MessageLiteOrBuilder>
      extends SubjectFactory<S, M> {}

  /**
   * Returns a SubjectFactory for {@link MessageLite} subjects which you can use to assert things
   * about Lite Protobuf properties.
   */
  public static Factory<?, MessageLiteOrBuilder> protoLite() {
    return UntypedSubjectFactory.INSTANCE;
  }

  /**
   * Returns a Subject using the assertion strategy on the provided {@link MessageLiteOrBuilder}, by
   * converting the argument to a {@link MessageLite}, using {@link
   * com.google.protobuf.MessageLite.Builder#buildPartial()} if needed.
   *
   * <p>Additionally, calling {@link #isEqualTo(Object)} will invoke {@code buildPartial} if needed.
   * Builders never compare equal to anything except themselves, so you rarely ever want to check
   * for equality with them.
   */
  public static ProtoLiteSubject<?, ?> assertThat(@Nullable MessageLiteOrBuilder m) {
    return assertAbout(protoLite()).that(m);
  }

  @SuppressWarnings("unchecked")
  protected ProtoLiteSubject(FailureStrategy failureStrategy, @Nullable M messageLiteOrBuilder) {
    super(failureStrategy, (M) buildPartial(messageLiteOrBuilder));
  }

  protected MessageLite getMessageLiteSubject() {
    // Guaranteed by 'getSubject()'
    return (MessageLite) getSubject();
  }

  // It is wrong to compare protos using their string representations. The MessageLite runtime
  // deliberately prefixes debug strings with their Object.toString() to discourage string
  // comparison. However, this reads poorly in tests, and makes it harder to identify differences
  // from the strings alone. So, we manually strip this prefix.
  // In case the class names are actually relevant, Subject.isEqualTo() will add them back for us.
  // TODO(user): Maybe get a way to do this upstream.
  private static String getTrimmedToString(@Nullable MessageLiteOrBuilder messageLiteOrBuilder) {
    String subjectString = String.valueOf(messageLiteOrBuilder).trim();
    if (subjectString.startsWith("# ")) {
      String objectToString =
          String.format(
              "# %s@%s",
              messageLiteOrBuilder.getClass().getName(),
              Integer.toHexString(messageLiteOrBuilder.hashCode()));
      if (subjectString.startsWith(objectToString)) {
        subjectString = subjectString.replaceFirst(Pattern.quote(objectToString), "").trim();
      }
    }

    return subjectString.isEmpty() ? "[empty proto]" : subjectString;
  }

  protected String getTrimmedDisplaySubject() {
    if (internalCustomName() != null) {
      return internalCustomName() + " (<" + getTrimmedToString(getSubject()) + ">)";
    } else {
      return "<" + getTrimmedToString(getSubject()) + ">";
    }
  }

  /**
   * Checks whether the MessageLite is equivalent to the argument, using the standard equals()
   * implementation.
   *
   * <p>If passed a {@link com.google.protobuf.MessageLite.Builder}, it calls {@code buildPartial()}
   * on the argument before doing comparison. Builders never compare equal to anything except
   * themselves, so you never really want to check equality with a Builder.
   */
  @Override
  public void isEqualTo(@Nullable Object expected) {
    // TODO(user): Do better here when MessageLite descriptors are available.
    if (expected instanceof MessageLiteOrBuilder) {
      expected = buildPartial((MessageLiteOrBuilder) expected);
    }
    if (!Objects.equal(getSubject(), expected)) {
      if (getSubject() == null || !(expected instanceof MessageLiteOrBuilder)) {
        super.isEqualTo(expected);
      } else {
        String ourString = getTrimmedToString(getSubject());
        String theirString = getTrimmedToString((MessageLite) expected);
        if (!ourString.equals(theirString)) {
          failureStrategy.failComparing("Not true that protos are equal:", theirString, ourString);
        } else if (getSubject().getClass() != expected.getClass()) {
          failureStrategy.failComparing(
              "Not true that protos are equal:",
              String.format("(%s) %s", expected.getClass().getName(), theirString),
              String.format("(%s) %s", getSubject().getClass().getName(), ourString));
        } else {
          // This will include the Object.toString() headers.
          super.isEqualTo(expected);
        }
      }
    }
  }

  @Override
  public void isNotEqualTo(@Nullable Object expected) {
    if (expected instanceof MessageLiteOrBuilder) {
      expected = buildPartial((MessageLiteOrBuilder) expected);
    }
    if (Objects.equal(getSubject(), expected)) {
      if (getSubject() == null) {
        super.isNotEqualTo(expected);
      } else {
        failWithRawMessage(
            "Not true that protos are different. Both are (%s) <%s>.",
            getSubject().getClass().getName(), getTrimmedToString(getSubject()));
      }
    }
  }

  /** Checks whether the subject is a {@link MessageLite} with no fields set. */
  public void isEqualToDefaultInstance() {
    if (getSubject() == null) {
      failWithRawMessage(
          "Not true that %s is a default proto instance. It is null.", getTrimmedDisplaySubject());
    } else if (!getSubject().equals(getSubject().getDefaultInstanceForType())) {
      failWithRawMessage(
          "Not true that %s is a default proto instance. It has set values.",
          getTrimmedDisplaySubject());
    }
  }

  /** Checks whether the subject is not equivalent to a {@link MessageLite} with no fields set. */
  public void isNotEqualToDefaultInstance() {
    if (getSubject() != null && getSubject().equals(getSubject().getDefaultInstanceForType())) {
      failWithRawMessage(
          "Not true that (%s) %s is not a default proto instance. It has no set values.",
          getSubject().getClass().getName(), getTrimmedDisplaySubject());
    }
  }

  /**
   * Checks whether the subject has all required fields set. Cannot fail for a proto built with
   * {@code build()}, which itself fails if required fields aren't set.
   */
  public void hasAllRequiredFields() {
    if (!getSubject().isInitialized()) {
      // MessageLite doesn't support reflection so this is the best we can do.
      failWithRawMessage(
          "Not true that %s has all required fields set. "
              + "(Lite runtime could not determine which fields were missing.)",
          getTrimmedDisplaySubject());
    }
  }

  /**
   * Returns an {@link IntegerSubject} on the serialized size of the MessageLite.
   *
   * <p>Assertions can then be changed on the serialized size, to support checks such as {@code
   * assertThat(myProto).serializedSize().isAtLeast(16)}, etc.
   */
  public IntegerSubject serializedSize() {
    return new TestVerb(failureStrategy)
        .that(getMessageLiteSubject().getSerializedSize())
        .named("sizeOf(" + getTrimmedDisplaySubject() + ")");
  }

  private static final class UntypedSubject
      extends ProtoLiteSubject<UntypedSubject, MessageLiteOrBuilder> {
    private UntypedSubject(
        FailureStrategy failureStrategy, @Nullable MessageLiteOrBuilder messageLiteOrBuilder) {
      super(failureStrategy, messageLiteOrBuilder);
    }
  }

  private static final class UntypedSubjectFactory
      extends Factory<UntypedSubject, MessageLiteOrBuilder> {
    private static final UntypedSubjectFactory INSTANCE = new UntypedSubjectFactory();

    @Override
    public UntypedSubject getSubject(
        FailureStrategy failureStrategy, MessageLiteOrBuilder messageLite) {
      return new UntypedSubject(failureStrategy, messageLite);
    }
  }

  private static MessageLite buildPartial(@Nullable MessageLiteOrBuilder m) {
    if (m instanceof MessageLite.Builder) {
      return ((MessageLite.Builder) m).buildPartial();
    } else {
      return (MessageLite) m;
    }
  }
}
