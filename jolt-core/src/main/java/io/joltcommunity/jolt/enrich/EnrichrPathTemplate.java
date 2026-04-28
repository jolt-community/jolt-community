/*
 * Copyright 2013 Bazaarvoice, Inc.
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
 */
package io.joltcommunity.jolt.enrich;

import io.joltcommunity.jolt.exception.SpecException;
import io.joltcommunity.jolt.traversr.SimpleTraversr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class EnrichrPathTemplate {

    private final String humanPath;
    private final List<Segment> segments;
    private final int wildcardCount;
    private final boolean hasAppendSegment;
    private final SimpleTraversr<Object> traversr;

    private EnrichrPathTemplate( String humanPath, List<Segment> segments, int wildcardCount, boolean hasAppendSegment ) {
        this.humanPath = humanPath;
        this.segments = Collections.unmodifiableList( new ArrayList<>( segments ) );
        this.wildcardCount = wildcardCount;
        this.hasAppendSegment = hasAppendSegment;
        this.traversr = new SimpleTraversr<>( humanPath );
    }

    static EnrichrPathTemplate parseInput( String humanPath, int index ) {
        EnrichrPathTemplate template = parse( humanPath, index, "path" );
        if ( template.hasAppendSegment ) {
            throw new SpecException(
                    "Enrichr enrichment at index:" + index +
                            " does not support '[]' in 'path'. Use explicit indices like '[0]' or array wildcards like '[*]'."
            );
        }
        return template;
    }

    static EnrichrPathTemplate parseOutput( String humanPath, int index ) {
        return parse( humanPath, index, "outputPath" );
    }

    private static EnrichrPathTemplate parse( String humanPath, int index, String fieldName ) {
        List<String> tokens = tokenize( humanPath );
        List<Segment> segments = new ArrayList<>( tokens.size() );
        int wildcardCount = 0;
        boolean hasAppendSegment = false;

        for ( String token : tokens ) {
            if ( "[]".equals( token ) ) {
                segments.add( Segment.append() );
                hasAppendSegment = true;
                continue;
            }

            if ( token.startsWith( "[" ) && token.endsWith( "]" ) ) {
                String innerValue = token.substring( 1, token.length() - 1 ).trim();
                if ( innerValue.isEmpty() ) {
                    throw new SpecException(
                            "Enrichr enrichment at index:" + index + " has an invalid '" + fieldName + "' segment '" + token + "'."
                    );
                }

                if ( "*".equals( innerValue ) ) {
                    segments.add( Segment.wildcard() );
                    wildcardCount++;
                    continue;
                }

                try {
                    int arrayIndex = Integer.parseInt( innerValue );
                    if ( arrayIndex < 0 ) {
                        throw new NumberFormatException( "negative" );
                    }
                    segments.add( Segment.arrayIndex( innerValue ) );
                    continue;
                }
                catch ( NumberFormatException e ) {
                    throw new SpecException(
                            "Enrichr enrichment at index:" + index + " has an invalid '" + fieldName + "' segment '" + token +
                                    "'. Supported array syntax is '[0]', '[1]', or '[*]'."
                    );
                }
            }

            segments.add( Segment.mapKey( token ) );
        }

        return new EnrichrPathTemplate( humanPath, segments, wildcardCount, hasAppendSegment );
    }

    private static List<String> tokenize( String humanPath ) {
        String intermediatePath = humanPath.replace( "[", ".[" ).replace( "..", "." );
        if ( intermediatePath.charAt( 0 ) == '.' ) {
            intermediatePath = intermediatePath.substring( 1 );
        }

        String[] rawKeys = intermediatePath.split( "\\." );
        List<String> keys = new ArrayList<>( rawKeys.length );
        Collections.addAll( keys, rawKeys );
        return keys;
    }

    List<EnrichrPathMatch> match( Object input ) {
        List<EnrichrPathMatch> matches = new ArrayList<>();
        collectMatches( input, 0, new ArrayList<String>(), new ArrayList<String>(), matches );
        return matches;
    }

    private void collectMatches(
            Object currentValue,
            int segmentIndex,
            List<String> resolvedKeys,
            List<String> wildcardBindings,
            List<EnrichrPathMatch> matches
    ) {
        if ( segmentIndex == segments.size() ) {
            matches.add( new EnrichrPathMatch( currentValue, resolvedKeys, wildcardBindings, resolvePath( wildcardBindings ) ) );
            return;
        }

        if ( currentValue == null ) {
            return;
        }

        Segment segment = segments.get( segmentIndex );
        switch ( segment.type ) {
            case MAP_KEY:
                if ( currentValue instanceof Map ) {
                    @SuppressWarnings( "unchecked" )
                    Map<String, Object> map = (Map<String, Object>) currentValue;
                    if ( map.containsKey( segment.value ) ) {
                        resolvedKeys.add( segment.value );
                        collectMatches( map.get( segment.value ), segmentIndex + 1, resolvedKeys, wildcardBindings, matches );
                        resolvedKeys.remove( resolvedKeys.size() - 1 );
                    }
                }
                return;

            case ARRAY_INDEX:
                if ( currentValue instanceof List ) {
                    List<?> list = (List<?>) currentValue;
                    int arrayIndex = Integer.parseInt( segment.value );
                    if ( arrayIndex < list.size() ) {
                        resolvedKeys.add( segment.value );
                        collectMatches( list.get( arrayIndex ), segmentIndex + 1, resolvedKeys, wildcardBindings, matches );
                        resolvedKeys.remove( resolvedKeys.size() - 1 );
                    }
                }
                return;

            case ARRAY_WILDCARD:
                if ( currentValue instanceof List ) {
                    List<?> list = (List<?>) currentValue;
                    for ( int index = 0; index < list.size(); index++ ) {
                        String resolvedIndex = String.valueOf( index );
                        resolvedKeys.add( resolvedIndex );
                        wildcardBindings.add( resolvedIndex );
                        collectMatches( list.get( index ), segmentIndex + 1, resolvedKeys, wildcardBindings, matches );
                        wildcardBindings.remove( wildcardBindings.size() - 1 );
                        resolvedKeys.remove( resolvedKeys.size() - 1 );
                    }
                }
                return;

            case ARRAY_APPEND:
                return;
        }
    }

    SimpleTraversr<Object> getTraversr() {
        return traversr;
    }

    int getWildcardCount() {
        return wildcardCount;
    }

    boolean hasAppendSegment() {
        return hasAppendSegment;
    }

    List<String> resolveKeys( List<String> wildcardBindings ) {
        validateBindings( wildcardBindings );

        List<String> resolvedKeys = new ArrayList<>( segments.size() );
        int wildcardIndex = 0;
        for ( Segment segment : segments ) {
            switch ( segment.type ) {
                case MAP_KEY:
                case ARRAY_INDEX:
                    resolvedKeys.add( segment.value );
                    break;
                case ARRAY_WILDCARD:
                    resolvedKeys.add( wildcardBindings.get( wildcardIndex++ ) );
                    break;
                case ARRAY_APPEND:
                    resolvedKeys.add( "[]" );
                    break;
            }
        }
        return resolvedKeys;
    }

    String resolvePath( List<String> wildcardBindings ) {
        validateBindings( wildcardBindings );

        StringBuilder pathBuilder = new StringBuilder();
        int wildcardIndex = 0;
        for ( int index = 0; index < segments.size(); index++ ) {
            if ( index > 0 ) {
                pathBuilder.append( '.' );
            }

            Segment segment = segments.get( index );
            switch ( segment.type ) {
                case MAP_KEY:
                    pathBuilder.append( segment.value );
                    break;
                case ARRAY_INDEX:
                    pathBuilder.append( '[' ).append( segment.value ).append( ']' );
                    break;
                case ARRAY_WILDCARD:
                    pathBuilder.append( '[' ).append( wildcardBindings.get( wildcardIndex++ ) ).append( ']' );
                    break;
                case ARRAY_APPEND:
                    pathBuilder.append( "[]" );
                    break;
            }
        }
        return pathBuilder.toString();
    }

    private void validateBindings( List<String> wildcardBindings ) {
        if ( wildcardBindings.size() < wildcardCount ) {
            throw new IllegalArgumentException(
                    "Expected at least " + wildcardCount + " wildcard bindings for path '" + humanPath + "', got " + wildcardBindings.size() + "."
            );
        }
    }

    private enum SegmentType {
        MAP_KEY,
        ARRAY_INDEX,
        ARRAY_WILDCARD,
        ARRAY_APPEND
    }

    private static final class Segment {
        private final SegmentType type;
        private final String value;

        private Segment( SegmentType type, String value ) {
            this.type = type;
            this.value = value;
        }

        private static Segment mapKey( String value ) {
            return new Segment( SegmentType.MAP_KEY, value );
        }

        private static Segment arrayIndex( String value ) {
            return new Segment( SegmentType.ARRAY_INDEX, value );
        }

        private static Segment wildcard() {
            return new Segment( SegmentType.ARRAY_WILDCARD, null );
        }

        private static Segment append() {
            return new Segment( SegmentType.ARRAY_APPEND, null );
        }
    }
}
