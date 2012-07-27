/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.view.visualization;

import com.google.common.collect.Lists;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import org.ros.android.view.visualization.layer.Layer;
import org.ros.message.MessageListener;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Subscriber;
import org.ros.rosjava_geometry.FrameTransformTree;

import java.util.List;

/**
 * @author moesenle@google.com (Lorenz Moesenlechner)
 */
public class VisualizationView extends GLSurfaceView implements NodeMain {

  private static final boolean DEBUG = false;

  private FrameTransformTree frameTransformTree;
  private Camera camera;
  private XYOrthographicRenderer renderer;
  private List<Layer> layers;
  private ConnectedNode connectedNode;

  public VisualizationView(Context context) {
    super(context);
    init();
  }

  public VisualizationView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    // TODO(damonkohler): Support ~tf_prefix parameter.
    frameTransformTree = new FrameTransformTree(NameResolver.newRoot());
    camera = new Camera(frameTransformTree);
    renderer = new XYOrthographicRenderer(frameTransformTree, camera);
    layers = Lists.newArrayList();
    if (DEBUG) {
      // Turn on OpenGL error-checking and logging.
      setDebugFlags(DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS);
    }
    setEGLConfigChooser(8, 8, 8, 8, 0, 0);
    getHolder().setFormat(PixelFormat.TRANSLUCENT);
    setRenderer(renderer);
  }

  @Override
  public GraphName getDefaultNodeName() {
    return GraphName.of("android_honeycomb_mr2/visualization_view");
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    for (Layer layer : Lists.reverse(layers)) {
      if (layer.onTouchEvent(this, event)) {
        return true;
      }
    }
    return false;
  }

  public XYOrthographicRenderer getRenderer() {
    return renderer;
  }

  /**
   * Adds a new layer at the end of the layers collection. The new layer will be
   * drawn last, i.e. on top of all other layers.
   * 
   * @param layer
   *          layer to add
   */
  public void addLayer(Layer layer) {
    layers.add(layer);
  }

  public void removeLayer(Layer layer) {
    layer.onShutdown(this, connectedNode);
    layers.remove(layer);
  }

  @Override
  public void onStart(ConnectedNode connectedNode) {
    this.connectedNode = connectedNode;
    startTransformListener();
    startLayers();
  }

  private void startTransformListener() {
    Subscriber<tf.tfMessage> tfSubscriber = connectedNode.newSubscriber("tf", tf.tfMessage._TYPE);
    tfSubscriber.addMessageListener(new MessageListener<tf.tfMessage>() {
      @Override
      public void onNewMessage(tf.tfMessage message) {
        for (geometry_msgs.TransformStamped transform : message.getTransforms()) {
          frameTransformTree.update(transform);
        }
      }
    });
  }

  private void startLayers() {
    for (Layer layer : layers) {
      layer.onStart(connectedNode, getHandler(), frameTransformTree, camera);
    }
    renderer.setLayers(layers);
  }

  @Override
  public void onShutdown(Node node) {
    renderer.setLayers(null);
    for (Layer layer : layers) {
      layer.onShutdown(this, node);
    }
    this.connectedNode = null;
  }

  @Override
  public void onShutdownComplete(Node node) {
  }

  @Override
  public void onError(Node node, Throwable throwable) {
  }
}
