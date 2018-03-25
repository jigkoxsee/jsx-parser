window.deps = {
    'react' : require('react'),
    'react-dom' : require('react-dom'),
    'react-dom-server': require('react-dom/server'),
    'babylon': require('babylon')
};

window.React = window.deps['react'];
window.ReactDOM = window.deps['react-dom'];
window.ReactDOMServer = window.deps['react-dom-server'];
