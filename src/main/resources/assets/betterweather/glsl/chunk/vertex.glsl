#version 330

layout (location = 0) in vec3 position;
layout (location = 1) in vec4 color;

out vec4 outColor;

uniform mat4 projection;
uniform mat4 modelView;

void main() {
    gl_Position = projection * modelView * vec4(position, 1.0);

    outColor = color;
}
